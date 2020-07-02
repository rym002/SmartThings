/**
 *  Jasco Z-Wave Motion Switch
 *
 *  Copyright 2020 Ray Munian
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
 
import groovy.json.JsonOutput
metadata {
	definition (name: "Jasco Z-Wave Motion Switch", namespace: "rym002", author: "Ray Munian", cstHandler: true, 
    			ocfDeviceType: "oic.d.light", mnmn: "SmartThings", vid:"generic-switch") {
		capability "Switch"
        capability "Configuration"
        capability "Health Check"

		attribute "groups", "number"
        
        command "setAssociationGroup", ["number", "enum", "number", "number"] // group number, nodes, action (0 - remove, 1 - add), multi-channel endpoint (optional)
        
		fingerprint mfr: "0063", prod: "494D", model: "3032", deviceJoinName: "GE Smart Motion Switch"
	}


	simulator {
		// TODO: define status and reply messages here
	}

	tiles {
		// TODO: define your main and details tiles here
	}
    preferences {
        input (
            title: 'Z Wave Configurations',
            description: 'Configurations available on the device',
            type: "paragraph",
            element: "paragraph"
        )
        parameterMap.each {
            input (
                    title: it.title,
                    description: it.description,
                    type: "paragraph",
                    element: "paragraph"
            )

            switch(it.type) {
                case "bool":
                    input(
                            type: "paragraph",
                            element: "paragraph",
                            description: "Option enabled: ${it.descriptions.true}\n" +
                                    "Option disabled: ${it.descriptions.false}"
                    )
                    input(
                            name: it.name,
                            type: "bool",
                            title: "Enable",
                            defaultValue: it.defaultValue == it.options["true"],
                            required: false
                    )
                    break
                case "enum":
                    input(
                            name: it.name,
                            title: "${it.title} Select",
                            type: "enum",
                            options: it.options,
                            defaultValue: it.defaultValue,
                            required: false
                    )
                    break
                case "number":
                    input(
                            name: it.name,
                            type: "number",
                            title: "${it.title} Set value (range ${it.range})",
                            defaultValue: it.defaultValue,
                            range: it.range,
                            required: false
                    )
                    break
            }
        }
        input (
            title: 'Tweaks',
            description: 'Tweaks for the device',
            type: "paragraph",
            element: "paragraph"
        )
        input(
            name: "commandDelay",
            type: "number",
            title: "Command Delay Duration",
            defaultValue: "200",
            range: "0..1000",
            required: false
        )
        input(
            name: "motionHandler",
            type: "enum",
            title: "Motion Handler Device",
            defaultValue: "0",
            options: [
                "0" : "Enable",
                "1" : "Disable"
            ],
            required: false
        )
        input (
            title: 'Data Sync',
            description: 'Data Sync for devices',
            type: "paragraph",
            element: "paragraph"
        )
        input(
            name: "syncSettings",
            type: "bool",
            title: "Sync Setting",
            description: "Force update all settings value from the device.",
            defaultValue: "false",
            required: false
        )
        input(
            name: "syncAssociations",
            type: "bool",
            title: "Sync Associations",
            description: "Force update all z wave associations from the device.",
            defaultValue: "false",
            required: false
        )
    }
}

def configure() {
    log.debug "configure"
    commands(initialize())
}

private initialize(){
    // Device-Watch simply pings if no device events received for checkInterval duration of 32min = 2 * 15min + 2min lag time
    sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
    def allCommands = childInit()
    allCommands
}

def installed() {
    log.debug "installed"
    createChildren()
    
    childDevices.each{
    	it.installed()
    }
    return response(refresh())
}

def updated() {
    log.debug "updated"
    createChildSensor()
    def allCommands = configurationDevice.updatePreferences()
    if (syncSettings){
        device.updateSetting "syncSettings", null
        allCommands += configurationDevice.allConfigGetCommands
    }
    if (syncAssociations){
        device.updateSetting "syncAssociations", null
        allCommands += associationDevice.updateAssociations()
    }
    allCommands ? response(commands(allCommands)) : null
}

def refresh() {
    log.debug "refresh"
	commands(childRefresh())
}


def parse(description) {
    def result = null
    if (description.startsWith("Err 106")) {
        result = createEvent(descriptionText: description, isStateChange: true)
    } else if (description != "updated") {
        def cmd = zwave.parse(description)
        if (cmd) {
            result = zwaveEvent(cmd)
            log.debug("'$description' parsed to $result")
        } else {
            log.debug("Couldn't zwave.parse '$description'")
        }
    }
    result
}
// handle commands
def on() {
	commands(switchDevice.on())
}

def off() {
	commands(switchDevice.off())
}

def setAssociationGroup(group, nodes, action, endpoint = null){
    associationDevice.setAssociationGroup(group, nodes, action, endpoint)
}

private zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	if (cmd.commandClass == 0x6C && cmd.parameter.size >= 4) { // Supervision encapsulated Message
		// Supervision header is 4 bytes long, two bytes dropped here are the latter two bytes of the supervision header
		cmd.parameter = cmd.parameter.drop(2)
		// Updated Command Class/Command now with the remaining bytes
		cmd.commandClass = cmd.parameter[0]
		cmd.command = cmd.parameter[1]
		cmd.parameter = cmd.parameter.drop(2)
	}
	def encapsulatedCommand = cmd.encapsulatedCommand()
    zwaveEvent(encapsulatedCommand)
}

private zwaveEvent(physicalgraph.zwave.Command cmd) {
	def childCmds = childDevices.collect{
    	it.zwaveEvent(cmd)
    }.findAll{
    	it!=1
    }.flatten()
    
    if (childCmds){
    	def realCmds = childCmds.findAll{
        	it!=null
        }
        if (realCmds){
        	return realCmds
        }
    }else{
        log.debug "Unhandled: $cmd"
    }
    null
}

private createChildren(){
	createChildSensor()
    createSwitchDevice()
    createAssociationDevice()
    createConfigurationDevice()
}

private findChildDevice(networkId){
	childDevices.find{
    	it.deviceNetworkId==networkId
    }
}

private getSensorDevice(){
	findChildDevice sensorDeviceId
}
private getSensorDeviceId(){
	"${device.deviceNetworkId}:Sensor"
}

private createChildSensor(){
    def name = "${device.displayName} Sensor"
    if (!sensorDevice && (motionHandler == null || motionHandler=="0")){
        addChildDevice("rym002", "Jasco Z-Wave Child Motion Sensor", sensorDeviceId , device.hubId,
                    [completedSetup: true, label: name, isComponent: false])
    }else if (sensorDevice && motionHandler=="1"){
        deleteChildDevice(sensorDeviceId)
    }
}

private getSwitchDeviceId(){
	"${device.deviceNetworkId}:Switch"
}
private getSwitchDevice(){
    findChildDevice switchDeviceId
}

private createSwitchDevice(){
    def name = "${device.displayName} Switch"
    if (!switchDevice){
        addChildDevice("rym002", "Jasco Z-Wave Child Switch", switchDeviceId , device.hubId,
                       [completedSetup: true, label: name, isComponent: true])
    }
}

private getAssociationDeviceId(){
	"${device.deviceNetworkId}:Association"
}
private getAssociationDevice(){
    findChildDevice associationDeviceId
}

private createAssociationDevice(){
    def name = "${device.displayName} Association"
    if (!associationDevice){
        addChildDevice("rym002", "Jasco Z-Wave Child Association", associationDeviceId , device.hubId,
                       [completedSetup: true, label: name, isComponent: true])
    }
}

private getConfigurationDeviceId(){
	"${device.deviceNetworkId}:Configuration"
}
private getConfigurationDevice(){
    findChildDevice configurationDeviceId
}

private createConfigurationDevice(){
    def name = "${device.displayName} Configuration"
    if (!configurationDevice){
        def config = addChildDevice("rym002", "Jasco Z-Wave Child Configuration", configurationDeviceId , device.hubId,
                       [completedSetup: true, label: name, isComponent: true])
        config.sendEvent(name:"parametersMap",value:JsonOutput.toJson(parameterMap))
    }
}
private getCommandDelay(){
    settings.commandDelay ? settings.commandDelay : 200
}

private commands(commands) {
    commands ? delayBetween(commands.collect { command(it) }, commandDelay) : commands
}

private command(physicalgraph.zwave.Command cmd) {
    if ((zwaveInfo.zw == null && state.sec != 0) || zwaveInfo?.zw?.contains("s")) {
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        cmd.format()
    }
}

private getParameterMap(){[
    [
        name: "timeoutDuration", type: "enum", title:"Timeout Duration",
        parameterNumber: 1, size: 1, defaultValue: "5",
        description: "Normal:Press any button on the switch. \nAlternate: Press two times ON button and two times OFF button, LED will flash 5 times if exclusion succeed",
        options:[
            "0":"Test (5s)",
            "1":"1 minute",
            "5":"5 minutes",
            "15":"15 minutes",
            "30":"30 minutes",
            "255":"Disable"
        ]
    ],
    [
        name: "brightness", type: "number", title:"Change brightness of associated light bulb(s)", range:"0..99",
        parameterNumber: 2, size: 1, defaultValue: "255",
        description: "brightness of associated light bulb(s)",
    ],
    [
        name: "operationMode", type: "enum", title:"Operation Mode",
        parameterNumber: 3, size: 1, defaultValue: "3",
        description: "Operation mode of the motion sensor",
        options:[
            "1": "Manual",
            "2": "Vacancy",
            "3": "Occupancy"
        ],
    ],
    [
        name: "associationMode", type: "enum", title:"Enable/Disable Association Mode",
        parameterNumber: 4, size: 1, defaultValue: "0",
        description: "Enable/Disable Association Mode",
        options:[
            "0":"Disabled",
            "1":"Enabled"
        ]
    ],
    [
        name: "invertSwitch", type: "enum", title:"Invert Switch",
        parameterNumber: 5, size: 1, defaultValue: "0",
        description: "Invert switch controls",
        options:[
            "0":"Disabled",
            "1":"Enabled"
        ]
    ],
    [
        name: "motionSensor", type: "enum", title:"Enable/Disable Motion Sensor",
        parameterNumber: 6, size: 1, defaultValue: "1",
        description: "Enable/Disable Motion Sensor",
        options:[
            "0":"Disabled",
            "1":"Enabled"
        ]
    ],
    [
        name: "motionSensorSensitivity", type: "enum", title:"Motion Sensor Sensitivity",
        parameterNumber: 13, size: 1, defaultValue: "2",
        description: "Motion Sensor Sensitivity",
        options:[
            "1":"High",
            "2":"Medium",
            "3":"Low"
        ]
    ],
    [
        name: "lightSensing", type: "enum", title:"Enable/Disable Light Sensing",
        parameterNumber: 14, size: 1, defaultValue: "1",
        description: "Enable/Disable Light Sensing",
        options:[
            "0":"Disabled",
            "1":"Enabled"
        ]
    ],
    [
        name: "resetCycle", type: "enum", title:"Reset Cycle",
        parameterNumber: 15, size: 1, defaultValue: "2",
        description: "Reset Cycle",
        options:[
            "0":"Disabled",
            "1":"10 secs",
            "2":"20 secs",
            "3":"30 secs",
            "4":"45 secs",
            "110":"27 mins"
        ]
    ],
    [
        name: "alternateExclusion", type: "enum", title:"Exclusion Mode",
        parameterNumber: 19, size: 1, defaultValue: "0",
        description: "Normal:Press any button on the switch. \nAlternate: Press two times ON button and two times OFF button, LED will flash 5 times if exclusion succeed",
        options:[
            "0":"Normal",
            "1":"Alternate"
        ]
    ]
]}

def childInit(){
	log.debug("Child Devices Init ${childDevices}")
	childDevices.collect{
    	it.initialize()
    }.flatten()
}

def childRefresh(){
	childDevices.collect{
    	it.childRefresh()
    }.flatten()
}

def setGroupsValue(groups){
    sendEvent(name: "groups", value: groups)
}

def getGroupsValue(){
	def groups = device.currentValue("groups")
    groups ? groups.toInteger() : null
}