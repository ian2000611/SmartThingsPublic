/**
 *  HVAC Auto Off
 *
 *  Author: dianoga7@3dgo.net
 *  Date: 2013-07-21
 */

// Automatically generated. Make future change here.
definition(
    name: "Thermostat Auto Off",
    namespace: "dianoga",
    author: "dianoga7@3dgo.net",
    description: "Automatically turn off thermostat when windows/doors open. Turn it back on when everything is closed up.",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png",
    oauth: true
)

preferences {
	section("Control") {
		input("thermostat", "capability.thermostat", title: "Thermostat")
	}
    
    section("Open/Close") {
    	input("sensors", "capability.contactSensor", title: "Sensors", multiple: true)
        input("delay", "number", title: "Delay (seconds)")
    }
	section( "Notifications" ) {
		input "sendPushMessage", "enum", title: "Send a push notification?", options: ["Yes", "No"], required: false
		input "phone", "phone", title: "Send a Text Message?", required: false
        input "notifications", "enum", title: "notify on which events?", options:["Debug","On","Off","Open","Close"], multiple:true
    }
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
def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
    unschedule()
	initialize()
}

def initialize() {
	state.changed = false
	subscribe(sensors, 'contact', "sensorChange")
}

def sensorChange(evt) {
	message("Debug", "Desc: $evt.value , $state")
    if(evt.value == 'open' && !state.changed) {
    	message("Open", "$evt.displayName was opened")
    	unschedule()
        runIn(delay, 'turnOff')
    } else if(evt.value == 'closed' && state.changed) {
    	// All closed?
        def isOpen = false
        message("Close", "$evt.displayName was closed")
        for(sensor in sensors) {
        	if(sensor.id != evt.deviceId && sensor.currentValue('contact') == 'open') {
        		isOpen = true
                message("Debug", "$sensor.name is still open")
            }
        }
        
        if(!isOpen) {
        	unschedule()
        	runIn(delay, 'restore')
        }
    }
}

def turnOff() {
	message("Off", "Turning off thermostat due to contact open")
	state.thermostatMode = thermostat.currentValue("thermostatMode")
	thermostat.off()
    state.changed = true
    log.debug "State: $state"
}

def restore() {
    message("On", "Setting thermostat to $state.thermostatMode")
    thermostat.setThermostatMode(state.thermostatMode)
    state.changed = false
}