/**
 * Jasco Z-Wave Scene Controller
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
import groovy.json.JsonOutput
metadata {
	definition (name: "Jasco Z-Wave Scene Controller", namespace: "rym002", author: "SmartThings", ocfDeviceType: "x.com.st.d.remotecontroller", mnmn: "SmartThings", vid: "generic-button-4") {
		capability "Button"
		capability "Sensor"
	}
}

def initialize(){
    [
        zwave.centralSceneV1.centralSceneSupportedGet()
    ]
}

def childRefresh(){
	[]
}
def installed() {
    sendEvent(name:"numberOfButtons", value: 1, displayed: false)
	sendEvent(name:"supportedButtonValues", value: sceneButtonNames.encodeAsJson(), displayed: false)
}

private getSceneButtonNames(){
    //6x is used for up/down release since there is no enum value
    ["up","up_6x","up_hold","up_2x","up_3x","down","down_6x","down_hold","down_2x","down_3x"]
}

public zwaveEvent(physicalgraph.zwave.commands.centralscenev1.CentralSceneSupportedReport cmd) {
    updateDataValue("supportedScenes", "${cmd.supportedScenes}")
}

public zwaveEvent(physicalgraph.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
    // keyAttributes: 0= click 1 = release 2 = hold  3 = double click, 4 = triple click
    // sceneNumber: 1=up 2=down
    log.debug "CentralSceneNotification ${cmd.keyAttributes} : ${cmd.sceneNumber} : ${cmd.sequenceNumber}"
    def modified = cmd.sceneNumber == 2 ? sceneButtonNames.size/2 : 0
    def eventIndex = cmd.keyAttributes + modified

    def buttonEvent = sceneButtonNames[eventIndex.intValue()]

    if (buttonEvent){
        sendEvent(name: "button", value: buttonEvent)
    }
}

public zwaveEvent(physicalgraph.zwave.Command cmd) {
    1
}

