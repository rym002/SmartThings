/**
 *  Jasco Z-Wave Child Configuration
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
	definition (name: "Jasco Z-Wave Child Configuration", namespace: "rym002", author: "SmartThings") {
        attribute "parametersMap", "string"
	}

	tiles(scale: 2) {
	}
}

def initialize(){
    [
        zwave.versionV1.versionGet(),
        zwave.firmwareUpdateMdV2.firmwareMdGet(),
        zwave.manufacturerSpecificV2.manufacturerSpecificGet()
    ] + allConfigGetCommands
}

def childRefresh(){
	[
    ]
}
def installed() {
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
    def preference = parameterMap.find( {it.parameterNumber == cmd.parameterNumber} )
    updatePreferenceValue preference, cmd.configurationValue[cmd.size-1]
}

def zwaveEvent(physicalgraph.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd) {
    updateDataValue("firmware", "${cmd.manufacturerId}-${cmd.firmwareId}-${cmd.checksum}")
}

def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
    updateDataValue("applicationVersion", "${cmd.applicationVersion}.${cmd.applicationSubVersion}")
    updateDataValue("zWaveProtocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
    updateDataValue("zWaveLibraryType", "${cmd.zWaveLibraryType}")
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
    updateDataValue("MSR", msr)
    updateDataValue("manufacturer", cmd.manufacturerName)
}

def zwaveEvent(physicalgraph.zwave.commands.applicationstatusv1.ApplicationBusy cmd) {
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

def zwaveEvent(physicalgraph.zwave.commands.applicationstatusv1.ApplicationRejectedRequest cmd) {
    createEvent(displayed: true, descriptionText: "${device.displayName} rejected the last request")
}

def zwaveEvent(physicalgraph.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
    log.info "Executing zwaveEvent 5A (DeviceResetLocallyV1) : 01 (DeviceResetLocallyNotification) with cmd: $cmd"
    createEvent(descriptionText: cmd.toString(), isStateChange: true, displayed: true)
}

def updatePreferences(){
    parameterMap.collect {
        def name = it.name
        def settingValue = parent.settings."$name"
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
                parent.device.updateSetting name, deviceScaledValue
            }
        }        
        
        if (parent.settings.syncSettings){
            if (it.defaultValue==deviceScaledValue){
                parent.device.updateSetting name, null
            } else {
                parent.device.updateSetting name, deviceScaledValue
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

def getAllConfigGetCommands(){
    parameterMap.collect {
        zwave.configurationV1.configurationGet(parameterNumber: it.parameterNumber)
    }
}

private getParameterMap(){
	def parms = device.currentValue("parametersMap")
    parms ? new groovy.json.JsonSlurper().parseText(parms): null
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    1
}