/**
 * Jasco z-wave switch
 *
 * Creates a child button for the scene event
 *
 * Supports Z-Wave Association Tool
 *  See: https://community.inovelli.com/t/how-to-using-the-z-wave-association-tool-in-smartthings/1944 for info
 *
 * Creates child device for actions with button scenes. If there is a vid that supports button and switch, I would be glad to change
 *    Note: Button release action is presented as 6x because there is no release member for supportedButtonValues
 *
 * works with Honeywell 39348/ZW4008
 */

import groovy.json.JsonOutput
metadata {
    definition(name: "Jasco Z-Wave Switch", namespace: "rym002", author: "Ray Munian", 
        ocfDeviceType: "oic.d.switch", mnmn: "SmartThings", vid: "generic-switch", 
        runLocally: true, minHubCoreVersion: '000.019.00012', executeCommandsLocally: true, genericHandler: "Z-Wave") {
        capability "Health Check"
        capability "Switch"
        capability "Refresh"
        capability "Sensor"
        capability "Configuration"

        attribute "groups", "number"

        command "setAssociationGroup", ["number", "enum", "number", "number"] // group number, nodes, action (0 - remove, 1 - add), multi-channel endpoint (optional)

        fingerprint mfr: "0039", prod: "4952", model: "3135", deviceJoinName: "Honeywell In-Wall Smart Switch"
    }

    simulator {
    }

    tiles(scale: 2) {
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
            defaultValue: "0",
            range: "0..1000",
            required: false
        )
        input(
            name: "sceneHandler",
            type: "enum",
            title: "Scene Handler Device",
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
    
    response(refresh())
}

def uninstalled(){
    log.debug "uninstalled"
}
def updated() {
    log.debug "updated"
    createSceneDevice()
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

def on() {
	commands(switchDevice.on())
}

def off() {
	commands(switchDevice.off())
}

def ping() {
    log.debug "ping"
    refresh()
}

def refresh() {
    log.debug "refresh"
    commands(childRefresh())
}

def setAssociationGroup(group, nodes, action, endpoint = null){
    associationDevice.setAssociationGroup(group, nodes, action, endpoint)
}

private zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x25: 1])
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
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

private getParameterMap(){[
    [
        name: "ledIndication", type: "enum", title:"LED indication configuration",
        parameterNumber: 3, size: 1, defaultValue: "0",
        description: "Controls the LED behavior",
        options:[
            "0": "Device Off",
            "1": "Device On",
            "2": "Always Off",
            "3": "Always On"
        ]
    ],
    [
        name: "alternateExclusion", type: "enum", title:"Alternate Exclusion",
        parameterNumber: 19, size: 1, defaultValue: "0",
        description: "Normal:Press any button on the switch. \nAlternate: Press two times ON button and two times OFF button, LED will flash 5 times if exclusion succeed",
        options:[
            "0": "Normal",
            "1": "Alternate"
        ]
    ]
]}


private getCommandDelay(){
    settings.commandDelay ? settings.commandDelay : 200
}

def commands(commands) {
    commands ? delayBetween(commands.collect { command(it) }, commandDelay) : commands
}

private command(physicalgraph.zwave.Command cmd) {

    if ((zwaveInfo.zw == null && state.sec != 0) || zwaveInfo?.zw?.contains("s")) {
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        cmd.format()
    }
}

private createChildren(){
	createSceneDevice()
    createSwitchDevice()
    createAssociationDevice()
    createConfigurationDevice()
}

private findChildDevice(networkId){
	childDevices.find{
    	it.deviceNetworkId==networkId
    }
}

private getSceneDeviceId(){
	"${device.deviceNetworkId}:Scene"
}

private getSceneDevice(){
	findChildDevice sceneDeviceId
}

private createSceneDevice(){
    def name = "${device.displayName} Scenes"
    if (!sceneDevice && (sceneHandler == null || sceneHandler=="0")){
        addChildDevice("rym002", "Jasco Z-Wave Scene Controller", sceneDeviceId , device.hubId,
                       [completedSetup: true, label: name, isComponent: false])
    }else if (sceneDevice && sceneHandler=="1"){
        deleteChildDevice(sceneDeviceId)
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