//全局隐含一个page对象
exports.doPage=function(frame,circuit,plug,ctx){
	var doc=ctx.html(frame.relativePath(),"utf-8");
	var pidList = doc.getElementById("pidList");
	var obj=plug.site().getService('graphContainer');
	var pids=obj.processIds;
	 if(typeof pids!='undefined'&&typeof pids.length!='undefined'){
	for(var i=0;i<pids.length;i++){
	pidList.append("<li>"+pids[i]+"</li>");
	}
	}
	 circuit.content().writeBytes(doc.toString().getBytes());
	
}