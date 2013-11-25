/* 
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
var wsUri = "ws://127.0.0.1:8080/WhiteboardApp/whiteboardendpoint";
var websocket = new WebSocket(wsUri);
var output = document.getElementById("output");


websocket.onerror = function(evt) { onError(evt); };

function onError(evt) {
    writeToScreen('<span style="color: red;">ERROR:</span> ' + evt.data);
}


websocket.onopen = function(evt) { onOpen(evt); };

function writeToScreen(message) {
    output.innerHTML += message + "<br>";
}

function onOpen() {
    writeToScreen("Connected to " + wsUri);
}
// End test functions


