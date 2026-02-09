
//#region src/common/utilities/encoder.ts
function dateToTimestampProto(timeString) {
	if (typeof timeString === "undefined") {
		return;
	}
	const date = new Date(timeString);
	const seconds = Math.floor(date.getTime() / 1e3);
	let nanos = 0;
	if (timeString.length > 20) {
		const nanoString = timeString.substring(20, timeString.length - 1);
		const trailingZeroes = 9 - nanoString.length;
		nanos = parseInt(nanoString, 10) * Math.pow(10, trailingZeroes);
	}
	return {
		seconds,
		nanos
	};
}

//#endregion
exports.dateToTimestampProto = dateToTimestampProto;