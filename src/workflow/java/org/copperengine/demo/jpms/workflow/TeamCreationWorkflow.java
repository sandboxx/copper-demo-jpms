/*
 * Copyright 2018 SCOOP Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.copperengine.demo.jpms.workflow;

import org.copperengine.core.*;
import org.copperengine.demo.jpms.Person;
import org.copperengine.demo.jpms.TeamCreationAdapter;
import org.copperengine.demo.jpms.TeamCreationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@WorkflowDescription(alias = "TeamCreationWorkFlow", majorVersion = 1, minorVersion = 0, patchLevelVersion = 0)
public class TeamCreationWorkflow extends Workflow<TeamCreationRequest> {
    private static final Logger logger = LoggerFactory.getLogger(TeamCreationWorkflow.class);

    private transient TeamCreationAdapter adapter;

    @AutoWire
    public void setAdapter(TeamCreationAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public void main() throws Interrupt {
        // trigger the creation of the team leader
        String leaderCorrelationId = adapter.asyncCreateLeader(getData().isFemaleLeader());

        // wait asynchronously for the team leader to be created
        wait(WaitMode.ALL, 120, TimeUnit.SECONDS, leaderCorrelationId);

        // retrieve the team leader
        Response<Person> leaderResponse = getAndRemoveResponse(leaderCorrelationId);
        if(leaderResponse.isTimeout()) {
            logger.warn("Timeout for team leader with correlationId: {}", leaderCorrelationId);
            return;
        }
        Person leader = leaderResponse.getResponse();

        // best practice: set variables that are no longer needed to null in order to reduce the footprint
        leaderCorrelationId = null;
        leaderResponse = null;

        // trigger the creation of all team members
        int teamSize = getData().getTeamSize();
        String[] memberCorrelationIds = new String[teamSize];
        for(int i=0; i < teamSize; i++) {
            memberCorrelationIds[i] = adapter.asyncCreateTeamMember(leader);
        }

        // wait asynchronously for all team members to be created
        wait(WaitMode.ALL, 120, TimeUnit.SECONDS, memberCorrelationIds);

        // retrieve all team members
        List<Person> members = new ArrayList<>();
        for(int i=0; i < teamSize; i++) {
            Response<Person> memberResponse = getAndRemoveResponse(memberCorrelationIds[i]);
            if(memberResponse.isTimeout()) {
                logger.warn("Timeout for team member with correlationId: {}", memberCorrelationIds[i]);
            } else {
                members.add(memberResponse.getResponse());
            }
        }

        // display the created team
        logger.info("Team of {} from {}; {}", leader.getFullName(), leader.getLocation(),
                members.stream().map(Person::getFullName).collect(Collectors.joining(", ")));
    }
}
