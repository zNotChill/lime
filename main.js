
const fs = require('fs');
const path = require('path');
const parseFile = require('./parser/parser');

parseFile(fs.readFileSync(path.join(__dirname, "files", "main.lime"), 'utf8'));