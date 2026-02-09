import { error, warn } from "../../logger/index.mjs";
import { getApp } from "../app.mjs";
import { dateToTimestampProto } from "../utilities/encoder.mjs";
import * as firestore from "firebase-admin/firestore";
import { google } from "../../../../protos/compiledFirestore.mjs";

//#region src/common/providers/firestore.ts
/** static-complied protobufs */
const DocumentEventData = google.events.cloud.firestore.v1.DocumentEventData;
let firestoreInstance;
/** @hidden */
function _getValueProto(data, resource, valueFieldName) {
	const value = data?.[valueFieldName];
	if (typeof value === "undefined" || value === null || typeof value === "object" && !Object.keys(value).length) {
		return resource;
	}
	const proto = {
		fields: value?.fields || {},
		createTime: dateToTimestampProto(value?.createTime),
		updateTime: dateToTimestampProto(value?.updateTime),
		name: value?.name || resource
	};
	return proto;
}
/** @internal */
function createSnapshotFromProtobuf(data, path, databaseId) {
	if (!firestoreInstance) {
		firestoreInstance = firestore.getFirestore(getApp(), databaseId);
	}
	try {
		const dataBuffer = Buffer.from(data);
		const firestoreDecoded = DocumentEventData.decode(dataBuffer);
		return firestoreInstance.snapshot_(firestoreDecoded.value || path, null, "protobufJS");
	} catch (err) {
		error("Failed to decode protobuf and create a snapshot.");
		throw err;
	}
}
/** @internal */
function createBeforeSnapshotFromProtobuf(data, path, databaseId) {
	if (!firestoreInstance) {
		firestoreInstance = firestore.getFirestore(getApp(), databaseId);
	}
	try {
		const dataBuffer = Buffer.from(data);
		const firestoreDecoded = DocumentEventData.decode(dataBuffer);
		return firestoreInstance.snapshot_(firestoreDecoded.oldValue || path, null, "protobufJS");
	} catch (err) {
		error("Failed to decode protobuf and create a before snapshot.");
		throw err;
	}
}
/** @internal */
function createSnapshotFromJson(data, source, createTime, updateTime, databaseId) {
	if (!firestoreInstance) {
		firestoreInstance = databaseId ? firestore.getFirestore(getApp(), databaseId) : firestore.getFirestore(getApp());
	}
	const valueProto = _getValueProto(data, source, "value");
	let timeString = createTime || updateTime;
	if (!timeString) {
		warn("Snapshot has no readTime. Using now()");
		timeString = new Date().toISOString();
	}
	const readTime = dateToTimestampProto(timeString);
	return firestoreInstance.snapshot_(valueProto, readTime, "json");
}
/** @internal */
function createBeforeSnapshotFromJson(data, source, createTime, updateTime, databaseId) {
	if (!firestoreInstance) {
		firestoreInstance = databaseId ? firestore.getFirestore(getApp(), databaseId) : firestore.getFirestore(getApp());
	}
	const oldValueProto = _getValueProto(data, source, "oldValue");
	const oldReadTime = dateToTimestampProto(createTime || updateTime);
	return firestoreInstance.snapshot_(oldValueProto, oldReadTime, "json");
}

//#endregion
export { createBeforeSnapshotFromJson, createBeforeSnapshotFromProtobuf, createSnapshotFromJson, createSnapshotFromProtobuf };