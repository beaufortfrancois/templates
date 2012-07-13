default:
	coffee -c Handlebar.coffee
	sed 's/exports\["class"\]/window.Handlebar/' Handlebar.js > HandlebarBrowser.js
