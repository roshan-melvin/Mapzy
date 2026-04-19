#!/usr/bin/env node
import { loadStack } from "../runtime/loader.mjs";
import { stackToWire } from "../runtime/manifest.mjs";
import express from "express";
import fs from "fs/promises";
import * as path from "path";

//#region src/bin/firebase-functions.ts
function printUsageAndExit() {
	console.error(`
Usage: firebase-functions [functionsDir]

Arguments:
  - functionsDir: Directory containing source code for Firebase Functions.
`);
	process.exit(1);
}
let functionsDir = ".";
const args = process.argv.slice(2);
if (args.length > 1) {
	if (args[0] === "-h" || args[0] === "--help") {
		printUsageAndExit();
	}
	functionsDir = args[0];
}
function handleQuitquitquit(req, res, server) {
	res.send("ok");
	server.close();
}
if (process.env.FUNCTIONS_MANIFEST_OUTPUT_PATH) {
	void (async () => {
		const outputPath = process.env.FUNCTIONS_MANIFEST_OUTPUT_PATH;
		try {
			const dir = path.dirname(outputPath);
			try {
				await fs.access(dir, fs.constants.W_OK);
			} catch (e) {
				console.error(`Error: Cannot write to directory '${dir}': ${e instanceof Error ? e.message : String(e)}`);
				console.error("Please ensure the directory exists and you have write permissions.");
				process.exit(1);
			}
			const stack = await loadStack(functionsDir);
			const wireFormat = stackToWire(stack);
			await fs.writeFile(outputPath, JSON.stringify(wireFormat, null, 2));
			process.exit(0);
		} catch (e) {
			if (e.code === "ENOENT") {
				console.error(`Error: Directory '${path.dirname(outputPath)}' does not exist.`);
				console.error("Please create the directory or specify a valid path.");
			} else if (e.code === "EACCES") {
				console.error(`Error: Permission denied writing to '${outputPath}'.`);
				console.error("Please check file permissions or choose a different location.");
			} else if (e.message?.includes("Failed to generate manifest")) {
				console.error(e.message);
			} else {
				console.error(`Failed to generate manifest from function source: ${e instanceof Error ? e.message : String(e)}`);
			}
			if (e instanceof Error && e.stack) {
				console.error(e.stack);
			}
			process.exit(1);
		}
	})();
} else {
	let server = undefined;
	const app = express();
	app.get("/__/quitquitquit", (req, res) => handleQuitquitquit(req, res, server));
	app.post("/__/quitquitquit", (req, res) => handleQuitquitquit(req, res, server));
	if (process.env.FUNCTIONS_CONTROL_API === "true") {
		app.get("/__/functions.yaml", async (req, res) => {
			try {
				const stack = await loadStack(functionsDir);
				res.setHeader("content-type", "text/yaml");
				res.send(JSON.stringify(stackToWire(stack)));
			} catch (e) {
				console.error(e);
				const errorMessage = e instanceof Error ? e.message : String(e);
				res.status(400).send(`Failed to generate manifest from function source: ${errorMessage}`);
			}
		});
	}
	let port = 8080;
	if (process.env.PORT) {
		port = Number.parseInt(process.env.PORT);
	}
	console.log("Serving at port", port);
	server = app.listen(port);
}

//#endregion
export {  };