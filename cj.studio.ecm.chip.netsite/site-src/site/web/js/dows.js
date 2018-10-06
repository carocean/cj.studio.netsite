var frame = {
	toFrame : function(frameRaw) {
		//debugger;
		var up = 0;
		var down = 0;
		var field = 0;// 0=heads;1=params;2=content
		var frame = {
			heads : {},
			params : {}
		};
		while (down < frameRaw.length) {
			if (frameRaw[up] == '\r'
					&& (up + 1 < frameRaw.length && frameRaw[up + 1] == '\n')) {// 跳域
				field++;
				up += 2;
				down += 2;
				continue;
			}
			if (field == 2) {
				down = frameRaw.length;
				var content = frameRaw.substr(up, down - up);
				frame.content = content;
				break;
			}
			if (frameRaw[down] == '\r'
					&& (down + 1 < frameRaw.length && frameRaw[down + 1] == '\n')) {// 跳行
				var b = frameRaw.substr(up, down - up);
				switch (field) {
				case 0:
					var kv = b;
					var at = kv.indexOf("=");
					var k = kv.substr(0, at);
					var v = kv.substr(at + 1, kv.length);
					frame.heads[k]=v;
					break;
				case 1:
					kv = b;
					at = kv.indexOf("=");
					k = kv.substr(0, at);
					v = kv.substr(at + 1, kv.length);
					frame.params[k]=v;
					break;
				}
				down += 2;
				up = down;
				continue;
			}
			down++;
		}
		return frame;
	}
}
function openit(url) {
	$.cj.socket.onmessage = function(e) {
		// debugger;
		var f = frame.toFrame(e.data);
		var text = document.getElementById("text");
		var cookiectr = document.getElementById("cookie");
		text.value = f.content;
		var cookie=f.heads['Set-Cookie']!='undefined'?f.heads['Set-Cookie']:'';
		if(typeof(cookie)!='undefined'){
			cookiectr.value=cookie;
		}
		
	}
	$.cj.socket.init(url, function(e) {
		var test = document.getElementById("test");
		test.innerHTML = 'open';
		// alert('open');
	}, function(e) {
		var test = document.getElementById("test");
		test.innerHTML = 'close';
	});
}
function sendit() {
	var text = document.getElementById("text");
	// var
	var cookie = document.getElementById("cookie");
	var frame = 'command=read\r\nurl=/\r\nprotocol=do/1.1\r\nCookie='+cookie.value+'\r\n\r\n\r\n'
	+ text.value;
	//var frame = text.value;
	$.cj.socket.send(frame);
}
function read2txt() {
	var text = document.getElementById("text");
	var cookie = document.getElementById("cookie");
	var frame = 'command=read\r\nurl=/read/\r\nprotocol=do/1.1\r\nCookie='+cookie.value+'\r\n\r\n\r\n'
			+ text.value;
	// var frame=text.value;
	$.cj.socket.send(frame);
}