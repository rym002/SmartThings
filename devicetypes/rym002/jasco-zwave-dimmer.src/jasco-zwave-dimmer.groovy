/**
 * Jasco z-wave dimmer
 *
 * Creates a child button for the scene event
 * Supports Z-Wave Association Tool
 * 
 * works with Honeywell 39351/ZW3010
 */
 
import groovy.json.JsonOutput
metadata {
	definition(name: "Jasco Z-Wave Dimmer", namespace: "rym002", author: "Ray Munian", ocfDeviceType: "oic.d.light", mnmn: "SmartThings", vid: "generic-dimmer", runLocally: true, minHubCoreVersion: '000.019.00012', executeCommandsLocally: true, genericHandler: "Z-Wave") {
		capability "Switch Level"
		capability "Health Check"
		capability "Switch"
		capability "Refresh"
		capability "Sensor"
		capability "Configuration"

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
        input(
            name: "syncSettings",
            type: "bool",
            title: "Sync Setting",
            description: "Force update all settings value from the device.",
            defaultValue: false,
            required: false
        )
        input(
            name: "dimmingDuration",
            type: "number",
            title: "Dimming Duration",
            defaultValue: 0,
            range: "0..255",
            required: false
        )
        input(
            name: "commandDelay",
            type: "number",
            title: "Command Delay Duration",
            defaultValue: 0,
            range: "0..1000",
            required: false
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
    }
}

def configure() {
	log.debug "Configure"
}

def installed() {
	log.debug "installed"
	// Device-Watch simply pings if no device events received for checkInterval duration of 32min = 2 * 15min + 2min lag time
	sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
	
    createChildButton()
    
	def allCommands = []
	parameterMap.each {
    	updatePreferenceValue it
        allCommands << commands(zwave.configurationV2.configurationGet(parameterNumber: it.parameterNumber))
	}
    return allCommands
}

def updated() {
	log.debug "updated"
	def allCommands = []
	parameterMap.each {
    	def name = it.name
        def settingValue = settings."$name"
        def deviceValue = state."$name"
        def deviceScaledValue = deviceValue

		if (settingValue != null){
            if (it.type=="enum"){
           		settingValue = Integer.parseInt(settingValue)
                it.options.each { key,value ->
                    if (value==deviceValue){
                        deviceScaledValue = key
                    }
                }
            }else {
            	deviceScaledValue = Integer.parseInt(deviceValue)
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
                allCommands << 	commands([
                    zwave.configurationV2.configurationSet(scaledConfigurationValue: settingValue, parameterNumber: it.parameterNumber, size: it.size),
                    zwave.configurationV2.configurationGet(parameterNumber: it.parameterNumber)
                ], commandDelay)
            } else if (deviceValue == null) {
                log.warn "Preference ${name} no. ${it.parameterNumber} has no value. Please check preference declaration for errors."
            }
        }
	}
	if (settings.syncSettings){
		device.updateSetting "syncSettings", null
        allCommands += allConfigGetCommands
    }
    allCommands << deviceInfoCommands()
    sendHubCommand allCommands
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
	def allCommands = allConfigGetCommands
    
	allCommands << commands([
	    zwave.switchMultilevelV3.switchMultilevelGet(),
        zwave.powerlevelV1.powerlevelGet()
    ], commandDelay)
    
    allCommands
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
	// keyAttributes: 0= single click 3 = double click
    // sceneNumber: 1=up 2=down
	log.debug "CentralSceneNotification ${cmd.keyAttributes} : ${cmd.sceneNumber} : ${cmd.sequenceNumber}"
    if (cmd.keyAttributes==3){
    	def buttonEvent = sceneButtonNames[cmd.sceneNumber-1]
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
	updatePreferenceValue preference, cmd.scaledConfigurationValue
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

private zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "Unhandled: $cmd"
	null
}

private getParameterMap(){[
	[
		name: "switchMode", type: "enum", title:"Switch Mode",
        parameterNumber: 16, size: 1, defaultValue: 0,
        description: "Enable/Disable Switch Mode",
        options:[
            0:"Dimmer",
        	1:"Switch"
        ]
    ],
    [
    	name: "alternateExclusion", type: "enum", title:"Alternate Exclusion",
        parameterNumber: 19, size: 1, defaultValue: 0,
        description: "Normal:Press any button on the switch. \nAlternate: Press two times ON button and two times OFF button, LED will flash 5 times if exclusion succeed",
        options:[
        	0: "Normal",
            1: "Alternate"
        ]
    ],
    [
    	name: "ledIndication", type: "enum", title:"LED indication configuration", 
        options:[
        	0: "When On",
            1: "When Off",
            2: "Always Off",
            3: "Always On"
		],
		parameterNumber: 3, size: 1, defaultValue: 0,
        description: "Controls the LED behavior"
    ],
    [
    	name: "minDimThreashold", type: "number", title:"Minimum Dim Threshold", range:"1..99",
        parameterNumber: 30, size: 1, defaultValue: 1,
        description: "Set the minimum dimmer threshold when manually or remotely controlled"
    ],
    [
    	name: "maxBrightnessThreashold", type: "number", title:"Maximum Brightness Threshold", range:"1..99",
        parameterNumber: 31, size: 1, defaultValue: 99,
        description: "Set the maximum brightness threshold when manually or remotely controlled"
    ],
    [
    	name: "defaultBrightnessLevel", type: "number", title:"Default Brightness Level", range:"0..99",
        parameterNumber: 32, size: 1, defaultValue: 0,
        description: "Set the default brightness level that the dimmer will turn on when being turned on manually. 0 to Disable",
        disableValue: 0
    ],
    [
    	name: "dimRate", type: "enum", title:"Dim Up/Down Rate",
        parameterNumber: 6, size: 1, defaultValue: 0,
        description: "Dim up/down the light to the specified level by command except value O and FF",
        options:[
        	0: "Quickly",
            1: "Slowly"
        ]
    ]
]}

private updatePreferenceValue(preference, value = "default") {
	def integerValue = value == "default" ? preference.defaultValue : value.intValue()
    def dataValue
    if (preference.type =='enum'){
    	dataValue = preference.options[integerValue]
    } else {
    	dataValue = "${integerValue}"
	}
    updateDataValue(preference.name, dataValue)
}

private deviceInfoCommands(){
	commands([
        zwave.versionV1.versionGet(),
        zwave.firmwareUpdateMdV2.firmwareMdGet(),
        zwave.manufacturerSpecificV2.manufacturerSpecificGet(),
        zwave.centralSceneV1.centralSceneSupportedGet()
		],commandDelay
    )
}

private syncConfiguration() {
	sendHubCommand configurationCommands
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
	def ret = []
	parameterMap.each {
        ret << commands([zwave.configurationV1.configurationGet(parameterNumber: it.parameterNumber)],commandDelay)
	}
    ret
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
	def childButton = addChildDevice("rym002", "Child Button", "${device.deviceNetworkId}:1", device.hubId,
				[completedSetup: true, label: "${device.displayName} Scene", isComponent: false])
    childButton.sendEvent(name:"numberOfButtons", value: 1, displayed: false)
    childButton.sendEvent(name:"supportedButtonValues", value: sceneButtonNames.encodeAsJson(), displayed: false)
}

private getSceneButtonNames(){
	["up","down"]
}

def setDefaultAssociations() {
    def smartThingsHubID = (zwaveHubNodeId.toString().format( '%02x', zwaveHubNodeId )).toUpperCase()
    state.defaultG1 = [smartThingsHubID]
    state.defaultG2 = []
    state.defaultG3 = []
}

def setAssociationGroup(group, nodes, action, endpoint = null){
    if (!state."desiredAssociation${group}") {
        state."desiredAssociation${group}" = nodes
    } else {
        switch (action) {
            case 0:
                state."desiredAssociation${group}" = state."desiredAssociation${group}" - nodes
            break
            case 1:
                state."desiredAssociation${group}" = state."desiredAssociation${group}" + nodes
            break
        }
    }
}

def processAssociations(){
   def cmds = []
   setDefaultAssociations()
   def associationGroups = 5
   if (state.associationGroups) {
       associationGroups = state.associationGroups
   } else {
       if (infoEnable) log.info "${device.label?device.label:device.name}: Getting supported association groups from device"
       cmds <<  zwave.associationV2.associationGroupingsGet()
   }
   for (int i = 1; i <= associationGroups; i++){
      if(state."actualAssociation${i}" != null){
         if(state."desiredAssociation${i}" != null || state."defaultG${i}") {
            def refreshGroup = false
            ((state."desiredAssociation${i}"? state."desiredAssociation${i}" : [] + state."defaultG${i}") - state."actualAssociation${i}").each {
                if (it != null){
                    if (infoEnable) log.info "${device.label?device.label:device.name}: Adding node $it to group $i"
                    cmds << zwave.associationV2.associationSet(groupingIdentifier:i, nodeId:Integer.parseInt(it,16))
                    refreshGroup = true
                }
            }
            ((state."actualAssociation${i}" - state."defaultG${i}") - state."desiredAssociation${i}").each {
                if (it != null){
                    if (infoEnable) log.info "${device.label?device.label:device.name}: Removing node $it from group $i"
                    cmds << zwave.associationV2.associationRemove(groupingIdentifier:i, nodeId:Integer.parseInt(it,16))
                    refreshGroup = true
                }
            }
            if (refreshGroup == true) cmds << zwave.associationV2.associationGet(groupingIdentifier:i)
            else if (infoEnable) log.info "${device.label?device.label:device.name}: There are no association actions to complete for group $i"
         }
      } else {
         if (infoEnable) log.info "${device.label?device.label:device.name}: Association info not known for group $i. Requesting info from device."
         cmds << zwave.associationV2.associationGet(groupingIdentifier:i)
      }
   }
   return cmds
}

private zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd) {
    if (debugEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
    def temp = []
    if (cmd.nodeId != []) {
       cmd.nodeId.each {
          temp += it.toString().format( '%02x', it.toInteger() ).toUpperCase()
       }
    } 
    state."actualAssociation${cmd.groupingIdentifier}" = temp
    if (infoEnable) log.info "${device.label?device.label:device.name}: Associations for Group ${cmd.groupingIdentifier}: ${temp}"
    updateDataValue("associationGroup${cmd.groupingIdentifier}", "$temp")
}

private zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationGroupingsReport cmd) {
    if (debugEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
    sendEvent(name: "groups", value: cmd.supportedGroupings)
    if (infoEnable) log.info "${device.label?device.label:device.name}: Supported association groups: ${cmd.supportedGroupings}"
    state.associationGroups = cmd.supportedGroupings
}
