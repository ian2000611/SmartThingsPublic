/**
 *  Superdoor
 *
 *  Copyright 2015 http://www.github.com/ian2000611/superdoor
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
/**
 *  Auto Lock Door
 *
 *  Author: Ian Patterson (@ian2000611)
 *  Date: 2015-07-19
 *  URL: http://www.github.com/ian2000611/superdoor
 *
 * Copyright (C) 2015 Ian Patterson.
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions: The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
 * OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */


// Automatically generated. Make future change here.
definition(
    name: "Superdoor",
    namespace: "ian2000611",
    author: "http://www.github.com/ian2000611/superdoor",
    category: "Safety & Security",
    description: "Re-arm an unlocked door after x minutes if closed, and unlock when door is knocked on withing time limit of motion or presence",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)
preferences {
  section("Door") {
  		input "lock1", "capability.lock", title: "Lock?"
        input "openSensor", "capability.contactSensor", title: "Open/Closed?"
		input "knockSensor", "capability.accelerationSensor", title: "Knock?"
	}
	section("Trigers") {
		input "motionSensor", "capability.motionSensor",title:"Movement?"
		input "people", "capability.presenceSensor", multiple: true, title: "Someone Arrived"
	}
	section("Delays") {
		input "motionTimeout", "number", title: "Motion in the last ? minuters"
		input "arivalTimeout", "number", title: "Arival in the last ? minuters"
		input "relockAfter", "number", title: "Relock after ? minutes"
	}
	section( "Notifications" ) {
		input "sendPushMessage", "enum", title: "Send a push notification?", options: ["Yes", "No"], required: false
		input "phone", "phone", title: "Send a Text Message?", required: false
        input "notifications", "enum", title: "notify on which events?", options:["Lock","Unlock","Open","Close","Arrive","Leave","Knock"], multiple:true
    }
}

def installed() {
  initialize()
}

def updated() {
	unsubscribe()
  initialize()
}

def initialize()
{
	["Lock","Unlock","Open","Close","Arrive","Leave","Knock"].each { type ->
    	log.debug ( "${type}: " + (notifications.contains(type)?"true":"false"))
	}
	state.lastArival = 0
	state.lastMotion = 0
    lock1.capabilities.each {cap ->
    log.debug "This device supports the ${cap.name} capability"
	}
    lock1.supportedAttributes.each {cap ->
    log.debug "This device supports the ${cap.name} attribute"
	}
    lock1.supportedCommands.each {cap ->
    log.debug "This device supports the ${cap.name} command"
	}
    subscribe(lock1, "lock.locked", doorLocked)
	subscribe(lock1, "lock.unlocked", doorUnlocked)
	subscribe(openSensor, "contact.closed", doorClosed)
	subscribe(openSensor, "contact.open", doorOpen)
	subscribe(people, "presence.present", present)
	subscribe(people, "presence.not present", absent)
	subscribe(knockSensor, "acceleration.active", doorKnock)
	subscribe(motionSensor,"motion.active",doorMotion)
}

def message(type,text) {
	if (notifications.contains(type)) {
		log.info(text)
		if (!sendPushMessage) {
    		if (!phone) {
    	    	sendNotificationEvent(text)
	        } else {
        		sendNotification(text,[method:'phone','phone':phone])
        	}
    	} else {
	    	if (!phone) {
        		sendNotification(text,[method:'push'])
        	} else {
        		sendNotification(text,[method:'both','phone':phone])
    	    }
	    }
    }
}
def lockDoor()
{
    if((openSensor.latestValue("contact") == "closed")){
    	lock1.lock()
    } else {
    	runIn( 10, lockDoor )
    }
}

def unlockDoor()
{
	if ((openSensor.latestValue("contact") == "closed")) {
    	lock1.unlock()
    } else {
    	runIn( 5, unlockDoor )
    }
}

def present(evt)
{
    message ("Arrive",evt.descriptionText)
    state.lastArival = now()		
}

def absent(evt)
{
    message ("Leave",evt.descriptionText)
	state.lastArival = 0
}

def doorMotion(evt) {
    state.lastMotion = now()
}

def doorOpen(evt) {
    message ("Opened", evt.descriptionText)
}

def doorClosed(evt) {
    message ("Closed",evt.descriptionText)
    unschedule( lockDoor )
    def delay = relockAfter * 60
    runIn( delay, lockDoor )
}

def doorLocked(evt) {
    message ("Lock",evt.descriptionText)
    unschedule( lockDoor )
}

def doorUnlocked(evt) {
    message ("Unlock",evt.descriptionText)
    unschedule( lockDoor )
    def delay = relockAfter * 60          // runIn uses seconds
    runIn( delay, lockDoor )                // ...schedule to lock in x minutes.
}

def doorKnock(evt) {
   	state.lastKnock=now()
    message ("Knock",(knockSensor.label?:knockSensor.name)+ " was knocked on")
  	if(openSensor.latestValue("contact") == "closed") {
  		if (lock1.latestValue("lock") == "locked") {
    		if (state.lastMotion + timeOffset(motionTimeout) > now()) {
    			unlockDoor()
    		} else if (state.lastArival + timeOffset(arivalTimeout) > now()) {
       			unlockDoor();
			}
    	} else {
       		lockDoor()
       	}
	}
}
