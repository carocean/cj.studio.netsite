if(typeof $ !=='object')
$={};
if(typeof $.cj !=='object')
$.cj={};
if(typeof $.cj.socket!=='object')
$.cj.socket={
	init:function(wsurl,onopen,onclose){
		var socket;
		if (!window.WebSocket) {
			window.WebSocket = window.MozWebSocket;
		}
		if (window.WebSocket) {
			//new WebSocket("ws://localhost:9090/wssite");
			socket = new WebSocket(wsurl);
			socket.onmessage = $.cj.socket.onmessage;
			socket.onopen = onopen;
			socket.onclose = onclose;
		} else {
			alert("Your browser does not support Web Socket.");
		}
		$.cj.socket.ws=socket;
	},
	send:function(message){
		if (!window.WebSocket) {
			return;
		}
		if ($.cj.socket.ws.readyState == WebSocket.OPEN) {
			$.cj.socket.ws.send(message);
		} else {
			alert("The socket is not open.");
		}
	}
};
