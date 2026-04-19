const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_logger_index = require('../../logger/index.js');
const require_common_app = require('../app.js');
const require_common_utilities_encoder = require('../utilities/encoder.js');
let firebase_admin_firestore = require("firebase-admin/firestore");
firebase_admin_firestore = require_rolldown_runtime.__toESM(firebase_admin_firestore);
let protos_compiledFirestore = require("../../../protos/compiledFirestore");
protos_compiledFirestore = require_rolldown_runtime.__toESM(protos_compiledFirestore);

//#region src/common/providers/firestore.ts
/** static-complied protobufs */
const DocumentEventData = protos_compiledFirestore.google.events.cloud.firestore.v1.DocumentEventData;
let firestoreInstance;
/** @hidden */
function _getValueProto(data, resource, valueFieldName) {
	const value = data?.[valueFieldName];
	if (typeof value === "undefined" || value === null || typeof value === "object" && !Object.keys(value).length) {
		return resource;
	}
	const proto = {
		fields: value?.fields || {},
		createTime: require_common_utilities_encoder.dateToTimestampProto(value?.createTime),
		updateTime: require_common_utilities_encoder.dateToTimestampProto(value?.updateTime),
		name: value?.name || resource
	};
	return proto;
}
/** @internal */
function createSnapshotFromProtobuf(data, path, databaseId) {
	if (!firestoreInstance) {
		firestoreInstance = firebase_admin_firestore.getFirestore(require_common_app.getApp(), databaseId);
	}
	try {
		const dataBuffer = Buffer.from(data);
		const firestoreDecoded = DocumentEventData.decode(dataBuffer);
		return firestoreInstance.snapshot_(firestoreDecoded.value || path, null, "protobufJS");
	} catch (err) {
		require_logger_index.error("Failed to decode protobuf and create a snapshot.");
		throw err;
	}
}
/** @internal */
function createBeforeSnapshotFromProtobuf(data, path, databaseId) {
	if (!firestoreInstance) {
		firestoreInstance = firebase_admin_firestore.getFirestore(require_common_app.getApp(), databaseId);
	}
	try {
		const dataBuffer = Buffer.from(data);
		const firestoreDecoded = DocumentEventData.decode(dataBuffer);
		return firestoreInstance.snapshot_(firestoreDecoded.oldValue || path, null, "protobufJS");
	} catch (err) {
		require_logger_index.error("Failed to decode protobuf and create a before snapshot.");
		throw err;
	}
}
/** @internal */
function createSnapshotFromJson(data, source, createTime, updateTime, databaseId) {
	if (!firestoreInstance) {
		firestoreInstance = databaseId ? firebase_admin_firestore.getFirestore(require_common_app.getApp(), databaseId) : firebase_admin_firestore.getFirestore(require_common_app.getApp());
	}
	const valueProto = _getValueProto(data, source, "value");
	let timeString = createTime || updateTime;
	if (!timeString) {
		require_logger_index.warn("Snapshot has no readTime. Using now()");
		timeString = new Date().toISOString();
	}
	const readTime = require_common_utilities_encoder.dateToTimestampProto(timeString);
	return firestoreInstance.snapshot_(valueProto, readTime, "json");
}
/** @internal */
function createBeforeSnapshotFromJson(data, source, createTime, updateTime, databaseId) {
	if (!firestoreInstance) {
		firestoreInstance = databaseId ? firebase_admin_firestore.getFirestore(require_common_app.getApp(), databaseId) : firebase_admin_firestore.getFirestore(require_common_app.getApp());
	}
	const oldValueProto = _getValueProto(data, source, "oldValue");
	const oldReadTime = require_common_utilities_encoder.dateToTimestampProto(createTime || updateTime);
	return firestoreInstance.snapshot_(oldValueProto, oldReadTime, "json");
}

//#endregion
exports.createBeforeSnapshotFromJson = createBeforeSnapshotFromJson;
exports.createBeforeSnapshotFromProtobuf = createBeforeSnapshotFromProtobuf;
exports.createSnapshotFromJson = createSnapshotFromJson;
exports.createSnapshotFromProtobuf = createSnapshotFromProtobuf;