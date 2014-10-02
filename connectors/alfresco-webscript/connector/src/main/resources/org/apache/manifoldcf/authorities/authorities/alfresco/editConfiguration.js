<script type="text/javascript">
<!--
function checkConfig() {
	return true;
}

function checkConfigForSave() {
	if (editconnection.protocol.value == "") {
		alert("Protocol must not be empty!");
		SelectTab("Server");
		editconnection.protocol.focus();
		return false;
	}
	if (editconnection.hostname.value == "") {
		alert("Hostname must not be empty!");
		SelectTab("Server");
		editconnection.hostname.focus();
		return false;
	}
	if (editconnection.endpoint.value == "") {
		alert("Endpoint must not be empty!");
		SelectTab("Server");
		editconnection.endpoint.focus();
		return false;
	}
	return true;
}
// -->
</script>
