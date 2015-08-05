/**
 *  DCS Service Manager
 *
 *  Author: Ian Patterson
 */
definition(
	name: "DCS-930L (Connect)",
	author: "Ian Patterson",
	description: "Connects to DCS-930L Webcams",
	category: "SmartThings Labs",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

preferences {
	page(name:"DCSDiscovery", title:"Sonos Device Setup", content:"DCSDiscovery", refreshTimeout:5)
}
//PAGES
def DCSDiscovery()
{
	if(canInstallLabs())
	{
		int DCSRefreshCount = !state.DCSRefreshCount ? 0 : state.DCSRefreshCount as int
		state.DCSRefreshCount = DCSRefreshCount + 1
		def refreshInterval = 3

		def options = DCSDiscovered() ?: []

		def numFound = options.size() ?: 0

		if(!state.subscribe) {
			log.trace "subscribe to location"
			subscribe(location, null, locationHandler, [filterEvents:false])
			state.subscribe = true
		}

		//sonos discovery request every 5 //25 seconds
		if((DCSRefreshCount % 8) == 0) {
			discoverSonoses()
		}

		//setup.xml request every 3 seconds except on discoveries
		if(((DCSRefreshCount % 1) == 0) && ((DCSRefreshCount % 8) != 0)) {
			verifyDCS()
		}

		return dynamicPage(name:"DCSDiscovery", title:"Discovery Started!", nextPage:"", refreshInterval:refreshInterval, install:true, uninstall: true) {
			section("Please wait while we discover your DCS camera(s). Discovery can take five minutes or more, so sit back and relax! Select your device below once discovered.") {
				input "selectedDCS", "enum", required:false, title:"Select DCS (${numFound} found)", multiple:true, options:options
			}
		}
	}
	else
	{
		def upgradeNeeded = """To use SmartThings Labs, your Hub should be completely up to date.

To update your Hub, access Location Settings in the Main Menu (tap the gear next to your location name), select your Hub, and choose "Update Hub"."""

		return dynamicPage(name:"sonosDiscovery", title:"Upgrade needed!", nextPage:"", install:false, uninstall: true) {
			section("Upgrade") {
				paragraph "$upgradeNeeded"
			}
		}
	}
}

private discoverDCS()
{
	//consider using other discovery methods
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:Basic:1.0", physicalgraph.device.Protocol.LAN))
}


private verifyDCS() {
	def devices = getDCS().findAll { it?.value?.verified != true }

	if(devices) {
		log.warn "UNVERIFIED PLAYERS!: $devices"
	}

	devices.each {
		verifyDCS((it?.value?.ip + ":" + it?.value?.port))
	}
}

private verifyDCS(String deviceNetworkId) {

	log.trace "dni: $deviceNetworkId"
	String ip = getHostAddress(deviceNetworkId)

	log.trace "ip:" + ip

	sendHubCommand(new physicalgraph.device.HubAction("""GET /xml/device_description.xml HTTP/1.1\r\nHOST: $ip\r\n\r\n""", physicalgraph.device.Protocol.LAN, "${deviceNetworkId}"))
}

Map DCSsDiscovered() {
	def vDCSs= getVerifiedDCS()
	def map = [:]
	vDCSs.each {
		def value = "${it.value.name}"
		def key = it.value.ip + ":" + it.value.port
		map["${key}"] = value
	}
	map
}

def getDCS()
{
	state.DCSs = state.DCSs ?: [:]
}

def getVerifiedDCS()
{
	getDCS().findAll{ it?.value?.verified == true }
}

def installed() {
	log.trace "Installed with settings: ${settings}"
	initialize()}

def updated() {
	log.trace "Updated with settings: ${settings}"
	unschedule()
	initialize()
}

def uninstalled() {
	def devices = getChildDevices()
	log.trace "deleting ${devices.size()} Sonos"
	devices.each {
		deleteChildDevice(it.deviceNetworkId)
	}
}

def initialize() {
	// remove location subscription aftwards
	unsubscribe()
	state.subscribe = false

	unschedule()
	scheduleActions()

	if (selectedDCS) {
		addDCS()
	}

	scheduledActionsHandler()
}

def scheduledActionsHandler() {
	log.trace "scheduledActionsHandler()"
	syncDevices()
	refreshAll()

	// TODO - for auto reschedule
	if (!state.threeHourSchedule) {
		scheduleActions()
	}
}

private scheduleActions() {
	def sec = Math.round(Math.floor(Math.random() * 60))
	def min = Math.round(Math.floor(Math.random() * 60))
	def hour = Math.round(Math.floor(Math.random() * 3))
	def cron = "$sec $min $hour/3 * * ?"
	log.debug "schedule('$cron', scheduledActionsHandler)"
	schedule(cron, scheduledActionsHandler)

	// TODO - for auto reschedule
	state.threeHourSchedule = true
	state.cronSchedule = cron
}

private syncDevices() {
	log.trace "Doing DCS Device Sync!"
	//runIn(300, "doDeviceSync" , [overwrite: false]) //schedule to run again in 5 minutes

	if(!state.subscribe) {
		subscribe(location, null, locationHandler, [filterEvents:false])
		state.subscribe = true
	}

	discoverDCS()
}

private refreshAll(){
	log.trace "refreshAll()"
	childDevices*.refresh()
	log.trace "/refreshAll()"
}

def addSonos() {
	def players = getVerifiedDCS()
	def runSubscribe = false
	selectedDCS.each { dni ->
		def d = getChildDevice(dni)
		if(!d) {
			def newDCS = DCSs.find { (it.value.ip + ":" + it.value.port) == dni }
			log.trace "newDCS = $newDCS"
			log.trace "dni = $dni"
			d = addChildDevice("ian2000611", "DCS Camera", dni, newDCS?.value.hub, [label:"${newDCS?.value.name} DCS Camera"])
			log.trace "created ${d.displayName} with id $dni"

			d.setModel(newDCS?.value.model)
			log.trace "setModel to ${newPlayer?.value.model}"

			runSubscribe = true
		} else {
			log.trace "found ${d.displayName} with id $dni already exists"
		}
	}
}

def locationHandler(evt) {
	def description = evt.description
	def hub = evt?.hubId

	def parsedEvent = parseEventMessage(description)
	parsedEvent << ["hub":hub]

	if (parsedEvent?.ssdpTerm?.contains("urn:schemas-upnp-org:device:Basic:1.0"))
	{ //SSDP DISCOVERY EVENTS

		log.trace "sonos found"
		def DCSs = getDCS()

		if (!(DCSs."${parsedEvent.ssdpUSN.toString()}"))
		{ //DCS does not exist
			DCSs << ["${parsedEvent.ssdpUSN.toString()}":parsedEvent]
		}
		else
		{ // update the values

			log.trace "Device was already found in state..."

			def d = DCSs."${parsedEvent.ssdpUSN.toString()}"
			boolean deviceChangedValues = false

			if(d.ip != parsedEvent.ip || d.port != parsedEvent.port) {
				d.ip = parsedEvent.ip
				d.port = parsedEvent.port
				deviceChangedValues = true
				log.trace "Device's port or ip changed..."
			}

			if (deviceChangedValues) {
				def children = getChildDevices()
				children.each {
					if (it.getDeviceDataByName("mac") == parsedEvent.mac) {
						log.trace "updating dni for device ${it} with mac ${parsedEvent.mac}"
						it.setDeviceNetworkId((parsedEvent.ip + ":" + parsedEvent.port)) //could error if device with same dni already exists
					}
				}
			}
		}
	}
	else if (parsedEvent.headers && parsedEvent.body)
	{ // SONOS RESPONSES
		def headerString = new String(parsedEvent.headers.decodeBase64())
		def bodyString = new String(parsedEvent.body.decodeBase64())

		def type = (headerString =~ /Content-Type:.*/) ? (headerString =~ /Content-Type:.*/)[0] : null
		def body
		log.debug "SONOS REPONSE: ${headerString.split('\n').findAll{it.trim()}.join(', ')}, TYPE: ${type}, BODY: ${bodyString.size()} BYTES"
		if (type?.contains("xml"))
		{ // description.xml response (application/xml)
            log.trace "Is XML"
        	//log.trace "BODY: ${bodyString.encodeAsHTML()}"
			body = new XmlSlurper().parseText(bodyString)

			if (body?.device?.modelName?.text().startsWith("Sonos") && !body?.device?.modelName?.text().toLowerCase().contains("bridge") && !body?.device?.modelName?.text().contains("Sub"))
			{
				def sonoses = getSonosPlayer()
				def player = sonoses.find {it?.key?.contains(body?.device?.UDN?.text())}
				if (player)
				{
					player.value << [name:body?.device?.roomName?.text(),model:body?.device?.modelName?.text(), serialNumber:body?.device?.serialNum?.text(), verified: true]
				}
				else
				{
					log.error "/xml/device_description.xml returned a device that didn't exist"
				}
			}
		}
		else if(type?.contains("json"))
		{ //(application/json)
        	log.trace "Is JSON"
			body = new groovy.json.JsonSlurper().parseText(bodyString)
			log.trace "GOT JSON $body"
		}
		else {
        	log.grace "Is neither XML nor JSON"
        }
	}
	else {
		log.trace "cp desc: " + description
		//log.trace description
	}
}

private def parseEventMessage(Map event) {
	//handles sonos attribute events
	return event
}

private def parseEventMessage(String description) {
	def event = [:]
	def parts = description.split(',')
	parts.each { part ->
		part = part.trim()
		if (part.startsWith('devicetype:')) {
			def valueString = part.split(":")[1].trim()
			event.devicetype = valueString
		}
		else if (part.startsWith('mac:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.mac = valueString
			}
		}
		else if (part.startsWith('networkAddress:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.ip = valueString
			}
		}
		else if (part.startsWith('deviceAddress:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.port = valueString
			}
		}
		else if (part.startsWith('ssdpPath:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.ssdpPath = valueString
			}
		}
		else if (part.startsWith('ssdpUSN:')) {
			part -= "ssdpUSN:"
			def valueString = part.trim()
			if (valueString) {
				event.ssdpUSN = valueString
			}
		}
		else if (part.startsWith('ssdpTerm:')) {
			part -= "ssdpTerm:"
			def valueString = part.trim()
			if (valueString) {
				event.ssdpTerm = valueString
			}
		}
		else if (part.startsWith('headers')) {
			part -= "headers:"
			def valueString = part.trim()
			if (valueString) {
				event.headers = valueString
			}
		}
		else if (part.startsWith('body')) {
			part -= "body:"
			def valueString = part.trim()
			if (valueString) {
				event.body = valueString
			}
		}
	}

	event
}


/////////CHILD DEVICE METHODS
def parse(childDevice, description) {
	def parsedEvent = parseEventMessage(description)

	if (parsedEvent.headers && parsedEvent.body) {
		def headerString = new String(parsedEvent.headers.decodeBase64())
		def bodyString = new String(parsedEvent.body.decodeBase64())
		log.trace "parse() - ${bodyString}"

		def body = new groovy.json.JsonSlurper().parseText(bodyString)
	} else {
		log.trace "parse - got something other than headers,body..."
		return []
	}
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress(d) {
	def parts = d.split(":")
	def ip = convertHexToIP(parts[0])
	def port = convertHexToInt(parts[1])
	return ip + ":" + port
}

private Boolean canInstallLabs()
{
	return hasAllHubsOver("000.011.00603")
}

private Boolean hasAllHubsOver(String desiredFirmware)
{
	return realHubFirmwareVersions.every { fw -> fw >= desiredFirmware }
}

private List getRealHubFirmwareVersions()
{
	return location.hubs*.firmwareVersionString.findAll { it }
}