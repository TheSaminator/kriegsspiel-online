const WebSocket = require("ws");
const wsServer = new WebSocket.Server({port: Number(process.env.PORT)});

let ids = [];
let hosts = {};

const newId = (function () {
	let idCounter = 0;

	function newId() {
		let countId = (idCounter++).toString().padStart(5, "0");
		idCounter %= 100000;

		let randId = Math.floor(Math.random() * 100000).toString().padStart(5, "0");

		return countId + randId;
	}

	return newId;
})();

wsServer.on("connection", function (ws) {
	ws.on("message", function (msgString) {
		const msg = JSON.parse(msgString);

		switch (msg.type) {
			case "host-offer":
				const hostOffer = msg.offer;
				const offerId = newId();
				ids.push(offerId);
				hosts[offerId] = {"offer": hostOffer, "host": ws, "iceCandidates": []};
				ws.send(JSON.stringify({"type": "offer-id", "id": offerId}));
				ws.connId = offerId;
				ws.connType = "host";

				break;
			case "list-data":
				const hostList = ids.map(function (id) {
					return {"id": id, "offer": hosts[id].offer, "joined": "guest" in Object.keys(hosts[id])};
				}).filter(function (record) {
					return !record.joined;
				});

				ws.send(JSON.stringify({"type": "list-data", "list": hostList}));

				break;
			case "join-answer":
				const joinAnswer = msg.answer;
				const joinAnswerId = msg.id;
				const joinData = hosts[joinAnswerId];

				if (joinData === undefined || joinData.guest !== undefined) {
					ws.send(JSON.stringify({"type": "connection-error", "message": "That ID is no longer available"}));
				} else {
					ws.connId = joinAnswerId;
					ws.connType = "guest";

					joinData.guest = ws;
					joinData.iceCandidates.forEach(function (candidate) {
						ws.send(JSON.stringify({"type": "ice-candidate", "candidate": candidate}));
					});
					joinData.iceCandidates = undefined;

					joinData.host.send(JSON.stringify({"type": "answer-data", "answer": joinAnswer}));
				}

				break;
			case "ice-candidate":
				const iceCandidate = msg.candidate;
				const iceId = ws.connId;
				const hostData = hosts[iceId];
				const connType = ws.connType;

				if (hostData === undefined) {
					ws.send(JSON.stringify({"type": "connection-error", "message": "ICE negotiation failed"}));
				} else if (connType === "host") {
					if (hostData.guest !== undefined)
						hostData.guest.send(JSON.stringify({"type": "ice-candidate", "candidate": iceCandidate}));
					else
						hostData.iceCandidates.push(iceCandidate);
				} else {
					hostData.host.send(JSON.stringify({"type": "ice-candidate", "candidate": iceCandidate}));
				}

				break;
			default:
				// ignore
				break;
		}
	});

	ws.on("close", function () {
		if (ws.connType === "host") {
			ids = ids.filter(function (id) {
				return id !== ws.connId
			});
			hosts[ws.connId] = undefined;
		}
	});
});
