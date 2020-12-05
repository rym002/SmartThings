/**
 *  Provides a virtual switch that triggers a virtual motion sensor.
 *	The switch activates/deactivates teh motion sensor.
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
metadata {
    definition (name: "Virtual Motion Switch", namespace: "rym002", author: "Ray Munian", cstHandler: true, 
    			ocfDeviceType: "oic.d.light", mnmn: "SmartThingsCommunity", vid:"241c8035-0d43-3403-bf80-f1ab0c6b9d3c") {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"
		capability "Motion Sensor"
    }

    preferences {}
}

def parse(description) {
}

def on() {
    sendEvent(name: "switch", value: "on", isStateChange: true)
    sendEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion", isStateChange: true)
}

def off() {
    sendEvent(name: "switch", value: "off", isStateChange: true)
    sendEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped", isStateChange: true)
}

def installed() {
    off()
}