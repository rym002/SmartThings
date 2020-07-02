/**
 *  Child Motion Sensor
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
	definition (name: "Jasco Z-Wave Child Motion Sensor", namespace: "rym002", author: "SmartThings", ocfDeviceType: "x.com.st.d.sensor.motion", mnmn: "SmartThings", vid: "generic-motion") {
		capability "Motion Sensor"
	}

	tiles(scale: 2) {
	}
}

def initialize(){
    [
    	
    ]
}

def childRefresh(){
	[
    	zwave.notificationV3.notificationGet(notificationType:0x07)
    ]
}
def installed() {
    sendEvent(name:"motion", value:"inactive")
}

public zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
	if (cmd.notificationType == 0x07) {
        if (cmd.event == 0x08) {				// detected
            sendEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion")
        } else if (cmd.event == 0x00) {			// inactive
            sendEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped")
        }
    }
}

public zwaveEvent(physicalgraph.zwave.Command cmd) {
    1
}
