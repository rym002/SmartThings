/**
 *  Jasco Z-Wave Child Switch
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
	definition (name: "Jasco Z-Wave Child Switch", namespace: "rym002", author: "SmartThings") {
	}

	tiles(scale: 2) {
	}
}

def initialize(){
    [
    	
    ]
}

def childRefresh(){
    [zwave.switchBinaryV1.switchBinaryGet()]
}
def installed() {
}

def on(){
	toggleSwitch 0xFF
}

def off(){
	toggleSwitch 0x00
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    switchEvent cmd
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    switchEvent cmd
}

private toggleSwitch(value){
    [
        zwave.switchBinaryV1.switchBinarySet(switchValue: value),
        zwave.switchBinaryV1.switchBinaryGet()
    ]
}

private switchEvent(physicalgraph.zwave.Command cmd){
    def value = (cmd.value ? "on" : "off")
    return [createEvent(name: "switch", value: value)]
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    1
}
