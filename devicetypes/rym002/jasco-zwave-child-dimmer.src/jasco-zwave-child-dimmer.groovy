/**
 *  Jasco Z-Wave Child Switch Dimmer
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
	definition (name: "Jasco Z-Wave Child Dimmer", namespace: "rym002", author: "SmartThings") {
	}

	tiles(scale: 2) {
	}
}

def initialize(){
    [
    	
    ]
}

def childRefresh(){
    [zwave.switchMultilevelV3.switchMultilevelGet()]
}
def installed() {
}

def on(rawDimmingDuration) {
    changeSwitchLevel 0xFF, dimmingDurationValue(rawDimmingDuration)
}

def off(rawDimmingDuration) {
    changeSwitchLevel 0x00, dimmingDurationValue(rawDimmingDuration)
}

def setLevel(level, rate) {
    def intValue = level as Integer
    def newLevel = Math.max(Math.min(intValue, 99), 0)
    def levelRate = dimmingDurationValue(rate)
    changeSwitchLevel newLevel, levelRate
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    dimmerEvents cmd
}
def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
    dimmerEvents cmd
}

private dimmingDurationValue(rate){
    rate < 128 ? rate : 128 + Math.round(rate / 60)
}

private changeSwitchLevel(value, dimmingDuration = 0){
	[
        zwave.switchMultilevelV3.switchMultilevelSet(value: value, dimmingDuration: dimmingDuration),
        zwave.switchMultilevelV3.switchMultilevelGet()
    ]
}

private dimmerEvents(physicalgraph.zwave.Command cmd) {
    def value = (cmd.value ? "on" : "off")
    def result = [createEvent(name: "switch", value: value)]
    if (cmd.value && cmd.value <= 100) {
        result << createEvent(name: "level", value: cmd.value == 99 ? 100 : cmd.value)
    }
    return result
}


def zwaveEvent(physicalgraph.zwave.Command cmd) {
    1
}