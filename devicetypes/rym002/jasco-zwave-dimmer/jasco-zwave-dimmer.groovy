/**
 * Honeywell z-wave dimmer
 */

 metadata {
	definition(name: "Jasco Dimmer", namespace: "rym002", author: "Ray Munian", ocfDeviceType: "oic.d.switch", runLocally: true, minHubCoreVersion: '000.019.00012', executeCommandsLocally: true, genericHandler: "Z-Wave") {
		capability "Switch Level"
		capability "Actuator"
		capability "Health Check"
		capability "Switch"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Light"

        fingerprint mfr: "0039", prod: "4944", model: "3235", deviceJoinName: "Honeywell In-Wall Smart Dimmer"
	}

	simulator {
		status "on": "command: 2003, payload: FF"
		status "off": "command: 2003, payload: 00"
		status "09%": "command: 2003, payload: 09"
		status "10%": "command: 2003, payload: 0A"
		status "33%": "command: 2003, payload: 21"
		status "66%": "command: 2003, payload: 42"
		status "99%": "command: 2003, payload: 63"

		// reply messages
		reply "2001FF,delay 5000,2602": "command: 2603, payload: FF"
		reply "200100,delay 5000,2602": "command: 2603, payload: 00"
		reply "200119,delay 5000,2602": "command: 2603, payload: 19"
		reply "200132,delay 5000,2602": "command: 2603, payload: 32"
		reply "20014B,delay 5000,2602": "command: 2603, payload: 4B"
		reply "200163,delay 5000,2602": "command: 2603, payload: 63"
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
}
def installed() {
	// Device-Watch simply pings if no device events received for checkInterval duration of 32min = 2 * 15min + 2min lag time
	sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
}

def updated() {
	response(refresh())
}

def parse(description) {
	def result = null
	if (description.startsWith("Err 106")) {
		result = createEvent(descriptionText: description, isStateChange: true)
	} else if (description != "updated") {
		def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x70: 1, 0x98: 1])
		if (cmd) {
			result = zwaveEvent(cmd)
			log.debug("'$description' parsed to $result")
		} else {
			log.debug("Couldn't zwave.parse '$description'")
		}
	}
	result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off")
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off")
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off")
}

def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
	createEvent(name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false)
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x25: 1])
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "Unhandled: $cmd"
	null
}

def on() {
	commands([
		zwave.basicV1.basicSet(value: 0xFF),
		zwave.basicV1.basicGet()
	])
}

def off() {
	commands([
		zwave.basicV1.basicSet(value: 0x00),
		zwave.basicV1.basicGet()
	])
}

def ping() {
	refresh()
}

def poll() {
	refresh()
}

def refresh() {
	command(zwave.basicV1.basicGet())
}

private command(physicalgraph.zwave.Command cmd) {
	if ((zwaveInfo.zw == null && state.sec != 0) || zwaveInfo?.zw?.contains("s")) {
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		cmd.format()
	}
}

private commands(commands, delay = 200) {
	delayBetween(commands.collect { command(it) }, delay)
}

def setLevel(value) {
}

def setLevel(value, duration) {
}