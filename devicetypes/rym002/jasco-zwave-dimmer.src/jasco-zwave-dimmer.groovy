/**
 * Jasco z-wave dimmer
 *
 * Creates a child button for the scene event
 *
 * Supports Z-Wave Association Tool
 *  See: https://community.inovelli.com/t/how-to-using-the-z-wave-association-tool-in-smartthings/1944 for info
 *
 * Creates child device for actions with button scenes. If there is a vid that supports button and switch, I would be glad to change
 *    Note: Button release action is presented as 6x because there is no release member for supportedButtonValues
 *
 * works with Honeywell 39351/ZW3010
 */

import groovy.json.JsonOutput
metadata {
    definition(name: "Jasco Z-Wave Dimmer", namespace: "rym002", author: "Ray Munian", 
        ocfDeviceType: "oic.d.light", mnmn: "SmartThings", vid: "generic-dimmer", 
        runLocally: true, minHubCoreVersion: '000.019.00012', executeCommandsLocally: true, genericHandler: "Z-Wave") {
        capability "Switch Level"
        capability "Health Check"
        capability "Switch"
        capability "Refresh"
        capability "Sensor"
        capability "Configuration"

        attribute "groups", "number"

        command "setAssociationGroup", ["number", "enum", "number", "number"] // group number, nodes, action (0 - remove, 1 - add), multi-channel endpoint (optional)

        fingerprint mfr: "0039", prod: "4944", model: "3235", deviceJoinName: "Honeywell In-Wall Smart Dimmer"
    }

    simulator {
        status "on": "command: 2603, payload: FF"
        status "off": "command: 2603, payload: 00"
        status "09%": "command: 2603, payload: 09"
        status "10%": "command: 2603, payload: 0A"
        status "33%": "command: 2603, payload: 21"
        status "66%": "command: 2603, payload: 42"
        status "99%": "command: 2603, payload: 63"

        // reply messages
        reply "2001FF,delay 200,2602": "command: 2603, payload: FF"
        reply "200100,delay 200,2602": "command: 2603, payload: 00"
        reply "200119,delay 200,2602": "command: 2603, payload: 19"
        reply "200132,delay 200,2602": "command: 2603, payload: 32"
        reply "20014B,delay 200,2602": "command: 2603, payload: 4B"
        reply "200163,delay 200,2602": "command: 2603, payload: 63"
    }

    tiles(scale: 2) {
        multiAttributeTile(name: "switch", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
            tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00a0dc", nextState: "turningOff"
                attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
                attributeState "turningOn", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00a0dc", nextState: "turningOff"
                attributeState "turningOff", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
            }
            tileAttribute("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action: "switch level.setLevel"
            }
        }

        standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label: '', action: "refresh.refresh", icon: "st.secondary.refresh"
        }

        valueTile("level", "device.level", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "level", label: '${currentValue} %', unit: "%", backgroundColor: "#ffffff"
        }

        main(["switch"])
        details(["switch", "level", "refresh"])

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
            name: "dimmingDuration",
            type: "number",
            title: "Dimming Duration",
            defaultValue: "0",
            range: "0..255",
            required: false
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
    commands(initialize(), commandDelay)
}

private initialize(){
    // Device-Watch simply pings if no device events received for checkInterval duration of 32min = 2 * 15min + 2min lag time
    sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
    def allCommands = [
        zwave.versionV1.versionGet(),
        zwave.firmwareUpdateMdV2.firmwareMdGet(),
        zwave.manufacturerSpecificV2.manufacturerSpecificGet(),
        zwave.centralSceneV1.centralSceneSupportedGet(),
        zwave.powerlevelV1.powerlevelGet()
    ]  + processAssociations() + allConfigGetCommands
    allCommands
}
def installed() {
    log.debug "installed"
    createChildButton()
    response(refresh())
}

def uninstalled(){
    log.debug "uninstalled"
}
def updated() {
    log.debug "updated"
    createChildButton()
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
    changeSwitchLevel 0xFF, dimmingDuration
}

def off() {
    changeSwitchLevel 0x00, dimmingDuration
}

def ping() {
    log.debug "ping"
    refresh()
}

def setLevel(level) {
    setLevel level, rawDimmingDuration
}

def setLevel(level, rate) {
    def intValue = level as Integer
    def newLevel = Math.max(Math.min(intValue, 99), 0)
    def levelRate = dimmingDurationValue(rate)
    changeSwitchLevel newLevel, levelRate
}

def refresh() {
    log.debug "refresh"
    commands([zwave.switchMultilevelV3.switchMultilevelGet()], commandDelay)
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
    dimmerEvents cmd
}
private zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
    dimmerEvents cmd
}
private zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelSet cmd) {
    dimmerEvents cmd
}
private zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
    dimmerEvents cmd
}

private zwaveEvent(physicalgraph.zwave.commands.powerlevelv1.PowerlevelReport cmd) {
    createEvent(name: "power", value: powerLevel)
}

private zwaveEvent(physicalgraph.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
    // keyAttributes: 0= click 1 = release 2 = hold  3 = double click, 4 = triple click
    // sceneNumber: 1=up 2=down
    log.debug "CentralSceneNotification ${cmd.keyAttributes} : ${cmd.sceneNumber} : ${cmd.sequenceNumber}"
    def modified = cmd.sceneNumber == 2 ? sceneButtonNames.size/2 : 0
    def eventIndex = cmd.keyAttributes + modified

    def buttonEvent = sceneButtonNames[eventIndex.intValue()]

    if (buttonEvent && childDevices){
        childDevices[0].sendEvent(name: "button", value: buttonEvent)
    }
}

private zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x25: 1])
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

private zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
    def preference = parameterMap.find( {it.parameterNumber == cmd.parameterNumber} )
    updatePreferenceValue preference, cmd.configurationValue[cmd.size-1]
}

private zwaveEvent(physicalgraph.zwave.commands.applicationstatusv1.ApplicationBusy cmd) {
    def msg
    switch (cmd.status) {
        case 0:
            msg = "Try again later"
            break
        case 1:
            msg = "Try again in ${cmd.waitTime} seconds"
            break
        case 2:
            msg = "Request queued"
            break
         default:
             msg = "Sorry"
    }
    createEvent(displayed: true, descriptionText: "${device.displayName} is busy, ${msg}")
}

private zwaveEvent(physicalgraph.zwave.commands.applicationstatusv1.ApplicationRejectedRequest cmd) {
    createEvent(displayed: true, descriptionText: "${device.displayName} rejected the last request")
}

private zwaveEvent(physicalgraph.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
    log.info "Executing zwaveEvent 5A (DeviceResetLocallyV1) : 01 (DeviceResetLocallyNotification) with cmd: $cmd"
    createEvent(descriptionText: cmd.toString(), isStateChange: true, displayed: true)
}

private zwaveEvent(physicalgraph.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd) {
    updateDataValue("firmware", "${cmd.manufacturerId}-${cmd.firmwareId}-${cmd.checksum}")
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

private zwaveEvent(physicalgraph.zwave.commands.centralscenev1.CentralSceneSupportedReport cmd) {
    updateDataValue("supportedScenes", "${cmd.supportedScenes}")
}

private zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationGroupingsReport cmd) {
    def event = createEvent(name: "groups", value: cmd.supportedGroupings)
    [event]
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

private zwaveEvent(physicalgraph.zwave.Command cmd) {
    log.debug "Unhandled: $cmd"
    null
}

private getParameterMap(){[
    [
        name: "switchMode", type: "enum", title:"Switch Mode",
        parameterNumber: 16, size: 1, defaultValue: "0",
        description: "Enable/Disable Switch Mode",
        options:[
            "0":"Dimmer",
            "1":"Switch"
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
    ],
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
        name: "minDimThreashold", type: "number", title:"Minimum Dim Threshold", range:"1..99",
        parameterNumber: 30, size: 1, defaultValue: "1",
        description: "Set the minimum dimmer threshold when manually or remotely controlled"
    ],
    [
        name: "maxBrightnessThreashold", type: "number", title:"Maximum Brightness Threshold", range:"1..99",
        parameterNumber: 31, size: 1, defaultValue: "99",
        description: "Set the maximum brightness threshold when manually or remotely controlled"
    ],
    [
        name: "defaultBrightnessLevel", type: "number", title:"Default Brightness Level", range:"0..99",
        parameterNumber: 32, size: 1, defaultValue: "0",
        description: "Set the default brightness level that the dimmer will turn on when being turned on manually. 0 to Disable"
    ],
    [
        name: "dimRate", type: "enum", title:"Dim Up/Down Rate",
        parameterNumber: 6, size: 1, defaultValue: "0",
        description: "Dim up/down the light to the specified level by command except value O and FF",
        options:[
            "0": "Quickly",
            "1": "Slowly"
        ]
    ]
]}

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

private getRawDimmingDuration(){
    settings.dimmingDuration ? settings.dimmingDuration : 0
}
private dimmingDurationValue(rate){
    rate < 128 ? rate : 128 + Math.round(rate / 60)
}
private getDimmingDuration(){
    dimmingDurationValue(rawDimmingDuration)
}

private getCommandDelay(){
    settings.commandDelay ? settings.commandDelay : 200
}

private commands(commands, delay = 200) {
    delayBetween(commands.collect { command(it) }, delay)
}

private changeSwitchLevel(value, dimmingDuration = 0){
	commands([
        zwave.switchMultilevelV3.switchMultilevelSet(value: value, dimmingDuration: dimmingDuration),
        zwave.switchMultilevelV3.switchMultilevelGet()
    ], commandDelay)
}

private getAllConfigGetCommands(){
    parameterMap.collect {
        zwave.configurationV1.configurationGet(parameterNumber: it.parameterNumber)
    }
}
private command(physicalgraph.zwave.Command cmd) {

    if ((zwaveInfo.zw == null && state.sec != 0) || zwaveInfo?.zw?.contains("s")) {
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        cmd.format()
    }
}

private dimmerEvents(physicalgraph.zwave.Command cmd) {
    def value = (cmd.value ? "on" : "off")
    def result = [createEvent(name: "switch", value: value)]
    if (cmd.value && cmd.value <= 100) {
        result << createEvent(name: "level", value: cmd.value == 99 ? 100 : cmd.value)
    }
    return result
}

private createChildButton(){
    def name = "${device.displayName} Scenes"
    def id = "${device.deviceNetworkId}:1"
    if (!childDevices && (sceneHandler == null || sceneHandler=="0")){
        def childButton = addChildDevice("rym002", "Child Button", id , device.hubId,
                    [completedSetup: true, label: name, isComponent: false])
        childButton.sendEvent(name:"numberOfButtons", value: 1, displayed: false)
        childButton.sendEvent(name:"supportedButtonValues", value: sceneButtonNames.encodeAsJson(), displayed: false)
    }else if (childDevices && sceneHandler){
        deleteChildDevice(id)
    }
}

private getSceneButtonNames(){
    //6x is used for up/down release since there is no enum value
    ["up","up_6x","up_hold","up_2x","up_3x","down","down_6x","down_hold","down_2x","down_3x"]
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