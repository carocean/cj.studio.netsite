//全局隐含一个page对象
this.name='zzzzz';
print('----------------error-default.jss.js');
exports.doPage=function(frame,circuit,plug,ctx){
	print('处理错误')
	var doc=ctx.html(frame.relativePath());
	var code=doc.getElementById('code');
	var msg=doc.getElementById('msg');
	var cause=doc.getElementById('cause');
	var c=circuit;
	code.html(c.status());
	msg.html(c.message());
	cause.html(c.cause());
	circuit.content().writeBytes(doc.toString().getBytes());
}