/**
 *  Jasco Motion Switch
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
	definition (name: "Jasco Motion Switch", namespace: "rym002", author: "Ray Munian", cstHandler: true, 
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
    commands(initialize(), commandDelay)
}

private initialize(){
    // Device-Watch simply pings if no device events received for checkInterval duration of 32min = 2 * 15min + 2min lag time
    sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
    def allCommands = [
        zwave.versionV1.versionGet(),
        zwave.firmwareUpdateMdV2.firmwareMdGet(),
        zwave.manufacturerSpecificV2.manufacturerSpecificGet()
    ]  + childInit() + processAssociations() + allConfigGetCommands
    allCommands
}

def installed() {
    log.debug "installed"
    createChildSensor()
    
    childDevices.each{
    	it.installed()
    }
    return response(refresh())
}

def updated() {
    log.debug "updated"
    createChildSensor()
    def allCommands = updatePreferences()
    if (syncSettings){
        device.updateSetting "syncSettings", null
        allCommands += allConfigGetCommands
    }
    if (syncAssociations){
        device.updateSetting "syncAssociations", null
        allCommands += updateAssociations()
    }
    allCommands ? response(commands(allCommands, commandDelay)) : null
}

def refresh() {
    log.debug "refresh"
    return commands([ zwave.switchBinaryV1.switchBinaryGet()
                    ] + childDevices.collect{
                        it.refresh()
                    }.flatten() , commandDelay)
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
	toggleSwitch 0xFF
}

def off() {
	toggleSwitch 0x00
}

def setAssociationGroup(group, nodes, action, endpoint = null){
    log.debug "group: ${group} , nodes: ${nodes}, action: ${action}, endpoint: ${endpoint}"
    def name = "desiredAssociation${group}"
    if (!state."${name}") {
        state."${name}" = nodes
    } else {
        switch (action) {
            case 0:
                state."${name}" = state."${name}" - nodes
            break
            case 1:
                state."${name}" = state."${name}" + nodes
            break
        }
    }
}

private zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    switchEvent cmd
}

private zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    switchEvent cmd
}

private zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd) {
    def temp = []
    if (cmd.nodeId != []) {
       cmd.nodeId.each {
          def value = it.toString().format( '%02x', it.toInteger() ).toUpperCase()
          temp += value
       }
    }

    updateDataValue("associationGroup${cmd.groupingIdentifier}", JsonOutput.toJson(temp))
}

private zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationGroupingsReport cmd) {
    sendEvent(name: "groups", value: cmd.supportedGroupings)
    response(commands(processAssociations(),commandDelay))
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


private zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
    def preference = parameterMap.find( {it.parameterNumber == cmd.parameterNumber} )
    updatePreferenceValue preference, cmd.configurationValue[cmd.size-1]
}

private zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
    updateDataValue("applicationVersion", "${cmd.applicationVersion}.${cmd.applicationSubVersion}")
    updateDataValue("zWaveProtocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
    updateDataValue("zWaveLibraryType", "${cmd.zWaveLibraryType}")
}

private zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
    updateDataValue("MSR", msr)
    updateDataValue("manufacturer", cmd.manufacturerName)
}

private zwaveEvent(physicalgraph.zwave.Command cmd) {
	def childCmds = childDevices.collect{
    	it.zwaveEvent(cmd)
    }.findAll{
    	it!=1
    }
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

private createChildSensor(){
    def name = "${device.displayName} Sensor"
    def id = "${device.deviceNetworkId}:1"
    if (!childDevices && (motionHandler == null || motionHandler=="0")){
        def childSensor = addChildDevice("rym002", "Child Motion Sensor", id , device.hubId,
                    [completedSetup: true, label: name, isComponent: false])
    }else if (childDevices && motionHandler=="1"){
        deleteChildDevice(id)
    }
}

private toggleSwitch(value){
    commands([
        zwave.switchBinaryV1.switchBinarySet(switchValue: value),
        zwave.switchBinaryV1.switchBinaryGet()
    ], commandDelay)
}

private getCommandDelay(){
    settings.commandDelay ? settings.commandDelay : 200
}

private getAllConfigGetCommands(){
    parameterMap.collect {
        zwave.configurationV1.configurationGet(parameterNumber: it.parameterNumber)
    }
}

private switchEvent(physicalgraph.zwave.Command cmd){
    def value = (cmd.value ? "on" : "off")
    return [createEvent(name: "switch", value: value)]
}
private commands(commands, delay = 200) {
    delayBetween(commands.collect { command(it) }, delay)
}

private command(physicalgraph.zwave.Command cmd) {

    if ((zwaveInfo.zw == null && state.sec != 0) || zwaveInfo?.zw?.contains("s")) {
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        cmd.format()
    }
}

private getDefaultAssociations() {
    def smartThingsHubID = (zwaveHubNodeId.toString().format( '%02x', zwaveHubNodeId )).toUpperCase()
    [
        1 : [smartThingsHubID]
    ]
}

private updateAssociations(){
   def groups = device.currentValue("groups")
   if (groups){
           def da = defaultAssociations
        return (1..groups).collect{ groupId->
            state.remove("desiredAssociation${groupId}")
            updateDataValue("associationGroup${groupId}", null)
            return zwave.associationV2.associationGet(groupingIdentifier:groupId)
        }
    }else{
        return [zwave.associationV2.associationGroupingsGet()]
    }
}

private processAssociations(){
   def cmds = []
   def groups = device.currentValue("groups")
   if (groups){
       	def da = defaultAssociations
        cmds = (1..groups).collect{ groupId->
            def associationGroup = state."associationGroup${groupId}"
               def currentNodes = associationGroup ? new groovy.json.JsonSlurper().parseText(associationGroup): null
               if (currentNodes != null) {
                def nodeCmds = []
                def defaultNodes = da.containsKey(groupId) ? da[groupId] : []
                def desiredNodes = state."desiredAssociation${groupId}"
                
                if (desiredNodes!=null || defaultNodes) {                    
                    nodeCmds += ((desiredNodes? desiredNodes : [] + defaultNodes) - currentNodes).collect {
                        if (it != null) {
                            return zwave.associationV2.associationSet(groupingIdentifier:groupId, nodeId:Integer.parseInt(it,16))
                        }
                    }
                    
                    nodeCmds += ((currentNodes - defaultNodes) - desiredNodes).collect {
                        if (it != null) {
                            return zwave.associationV2.associationRemove(groupingIdentifier:groupId, nodeId:Integer.parseInt(it,16))
                        }
                    }
                                        
                    if (nodeCmds) {
                        nodeCmds +=  zwave.associationV2.associationGet(groupingIdentifier:groupId)
                    } else {
                        log.info "There are no association actions to complete for group ${groupId}"
                    }
                	return nodeCmds
                }
           } else {
               log.warn "Nodes not found for group ${groupId}"
               return [zwave.associationV2.associationGet(groupingIdentifier:groupId)]
           }
        }.collectMany{
            it == null ? [] : it
        }
       } else {
           log.warn "no groups found in state"
        cmds << zwave.associationV2.associationGroupingsGet()
       }
       return cmds
}

private updatePreferences(){
    parameterMap.collect {
        def name = it.name
        def settingValue = settings."$name"
        def deviceValue = state."$name"
        def deviceScaledValue = deviceValue
		def defaultValue = Short.parseShort(it.defaultValue)
        
		if (deviceValue==null){
            deviceValue = it.defaultValue
            deviceScaledValue = Short.parseShort(deviceValue)
        }else{
            if (it.type=="enum"){
            	settingValue = settingValue !=null ? Short.parseShort(settingValue) : null
                it.options.each { key,value ->
                    if (value==deviceValue){
                        deviceScaledValue = Short.parseShort(key)
                    }
                }
            }else {
                deviceScaledValue = Short.parseShort(deviceValue)
            }
        }

		if (settingValue == null){
        	if (deviceScaledValue != defaultValue) {
            	settingValue = defaultValue
            }else{
                device.updateSetting name, deviceScaledValue
            }
        }        
        
        if (settings.syncSettings){
            if (it.defaultValue==deviceScaledValue){
                device.updateSetting name, null
            } else {
                device.updateSetting name, deviceScaledValue
            }
        } else {
            if (settingValue!=null && deviceScaledValue != settingValue) {
                log.debug "Preference ${name} has been updated from value: ${deviceScaledValue} to ${settingValue}"
                return [
                    zwave.configurationV2.configurationSet(scaledConfigurationValue: settingValue, parameterNumber: it.parameterNumber, size: it.size),
                    zwave.configurationV2.configurationGet(parameterNumber: it.parameterNumber)
                ]
            } else if (deviceValue == null) {
                log.warn "Preference ${name} no. ${it.parameterNumber} has no value. Please check preference declaration for errors."
            }
        }
    }.collectMany{
        it == null ? [] : it
    }

}
private updatePreferenceValue(preference, value = "default") {
    def strValue = value == "default" ? preference.defaultValue : "${value}"
    def dataValue
    if (preference.type =='enum'){
        dataValue = preference.options[strValue]
    } else {
        dataValue = strValue
    }
    updateDataValue(preference.name, dataValue)
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