var showResource = true;
var showResourceType = false;


function showResourceBtn() {
	  var resourceBtn = document.getElementById('showResource');
	  var resourceTypeBtn = document.getElementById('showResourceType');
	  resourceTypeBtn.className = "typeButton";
	  if (showResource == false) {
	    resourceBtn.className = "typeButton selected";
	    var resourcewrapper = document.getElementById("resourceWrapper");
	    var resourcetypewrapper = document.getElementById("resourceTypeWrapper");
	    resourcewrapper.style.display = "block";
	    resourcetypewrapper.style.display = "none";
	    showResourceType = false;
	    showResource = true;
	  }
	}

function showResourceTypeBtn() {
	  var resourceBtn = document.getElementById('showResource');
	  var resourceTypeBtn = document.getElementById('showResourceType');
	  resourceBtn.className = "typeButton";
	  if (showResourceType == false) {
	    resourceTypeBtn.className = "typeButton selected";
	    var resourcewrapper = document.getElementById("resourceWrapper");
	    var resourcetypewrapper = document.getElementById("resourceTypeWrapper");
	    resourcewrapper.style.display = "none";
	    resourcetypewrapper.style.display = "block";
	    showResourceType = true;
	    showResource = false;
	  }
	}



function copyToClipboard(text)
{
    alert(text);
}

function onData(data) {
	window.resourceTimeline = new vis.Timeline(document.getElementById('resourceTimeline'));
	var options = {
			min: Number.MAX_VALUE,
			max: Number.MIN_VALUE,
			orientation: 'top',
			height: '100%',
			groupOrder: 'content', // groupOrder can be a property name or a sorting function
			margin: {item: 0, axis: 0}
			}; 
	var maxTime = 0;
	for (var i = 0; i < data.items.length; i++) {
		if (data.items[i].end != undefined)
			maxTime = Math.max(maxTime,data.items[i].end)
	}
	var finishedItems = [];
	for (var i = 0; i < data.items.length; i++) {
		data.items[i].start = new Date(data.items[i].start);
		options.min = Math.min(options.min, data.items[i].start);
		if (data.items[i].end != undefined)
		{
			data.items[i].end = new Date(data.items[i].end);
			options.max = Math.max(options.max, data.items[i].end);
		}
		else
		{
		///* else
			data.items[i].end = new Date(maxTime);// */
			//if(console.log)
			//	console.log('Ignoring item without end time: '+JSON.stringify(data.items[i]));
			options.max = Math.max(options.max, data.items[i].start);
			continue;
		}				
		data.items[i].group = data.items[i].group.id;
		
		data.items[i].content = data.items[i].content;
		data.items[i].title = data.items[i].title.replace('{interval}',data.items[i].start+'-'+data.items[i].end);
		finishedItems.push(data.items[i])
	}
	window.resourceTimeline.setOptions(options);
	window.resourceTimeline.setGroups(data.groups);
	window.resourceTimeline.setItems(data.items);
	window.resourceTimeline.on('select', onSelect);
}

function onTypeData(data) {
	window.resourceTypeTimeline = new vis.Timeline(document.getElementById('resourceTypeTimeline'));
	var options = {
			min: Number.MAX_VALUE,
			max: Number.MIN_VALUE,
			orientation: 'top',
			height: '100%',
			groupOrder: 'content', // groupOrder can be a property name or a sorting function
			margin: {item: 0, axis: 0}
			}; 
	var maxTime = 0;
	for (var i = 0; i < data.items.length; i++) {
		if (data.items[i].end != undefined)
			maxTime = Math.max(maxTime,data.items[i].end)
	}
	var finishedItems = [];
	for (var i = 0; i < data.items.length; i++) {
		data.items[i].start = new Date(data.items[i].start);
		options.min = Math.min(options.min, data.items[i].start);
		if (data.items[i].end != undefined)
		{
			data.items[i].end = new Date(data.items[i].end);
			options.max = Math.max(options.max, data.items[i].end);
		}
		else
		{
		///* else
			data.items[i].end = new Date(maxTime);// */
			//if(console.log)
			//	console.log('Ignoring item without end time: '+JSON.stringify(data.items[i]));
			options.max = Math.max(options.max, data.items[i].start);
			continue;
		}				
		data.items[i].group = data.items[i].group.id;
		
		data.items[i].content = data.items[i].content;
		data.items[i].title = data.items[i].title.replace('{interval}',data.items[i].start+'-'+data.items[i].end);
		finishedItems.push(data.items[i])
	}
	window.resourceTypeTimeline.setOptions(options);
	window.resourceTypeTimeline.setGroups(data.groups);
	window.resourceTypeTimeline.setItems(data.items);
	window.resourceTypeTimeline.on('select', onSelect);
}


function onSelect(nodes) {
	var cText = "";
	for (var i = 0; i < nodes.items.length; i++)
		for (var j = 0; j < data.items.length; j++)
			if (data.items[j].id == nodes.items[i])
				cText += data.items[j].title  + "\n\n";
	copyToClipboard(cText);
	console.log('Selected items: ' + JSON.stringify(nodes));
}
