/**
 *  Jasco Z-Wave Child Association
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
	definition (name: "Jasco Z-Wave Child Association", namespace: "rym002", author: "SmartThings") {
	}

	tiles(scale: 2) {
	}
}

def initialize(){
    processAssociations()
}

def childRefresh(){
	[
    ]
}
def installed() {
//	parent.sendEvent(name: "groups", value: null)
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

def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationGroupingsReport cmd) {
    parent.setGroupsValue(cmd.supportedGroupings)
    def groups = parent.groupsValue
   if (groups){
       (1..groups).each{ groupId->
           updateDataValue("associationGroup${groupId}", null)
       }
	}
    def associationCommands = processAssociations()
    response(parent.commands(associationCommands))
}

def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd) {
    def temp = []
    if (cmd.nodeId != []) {
       cmd.nodeId.each {
          def value = it.toString().format( '%02x', it.toInteger() ).toUpperCase()
          temp += value
       }
    }

    updateDataValue("associationGroup${cmd.groupingIdentifier}", JsonOutput.toJson(temp))
}

private getDefaultAssociations() {
    def smartThingsHubID = (zwaveHubNodeId.toString().format( '%02x', zwaveHubNodeId )).toUpperCase()
    def defaultAssociationIdsVal = state.defaultAssociationIds
    def defaultAssociationIds = defaultAssociationIdsVal?new groovy.json.JsonSlurper().parseText(defaultAssociationIdsVal): [1]
    defaultAssociationIds.collectEntries{
        [(it):[smartThingsHubID]]
    }
}

def updateAssociations(){
   def groups = parent.groupsValue
   if (groups){
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
    def groups = parent.groupsValue
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

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    1
}