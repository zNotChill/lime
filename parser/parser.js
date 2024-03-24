class Parser {
  constructor() {
    this.functions = {};
    this.variables = {};
    this.builtInFunctions = {
      print: {
        args: ["string"],
        run: (args) => {
          console.log(this.variables[args[0]] || args[0]);
          return 0;
        }
      },
      createHTTPServer: {
        args: ["port", "callback"],
        run: (args) => {
          let port = args[0];
          let callback = args[1];
          let server = require("http").createServer((req, res) => {
            // Parse the callback function and execute it
            let functionName = callback.split("() -> ")[1].trim();
            let functionArgs = [];
            if (this.functions[functionName] || this.builtInFunctions[functionName]) {
              this.runFunction(functionName, functionArgs);
            }
            res.end();
          });
          server.listen(port);
          return 0;
        }
      },
    };
  }

  runFunction(functionName, functionArgs) {
    let func = this.functions[functionName] || this.builtInFunctions[functionName];
    if (func) {
      let args = [];
      for (let i = 0; i < functionArgs.length; i++) {
        let arg = functionArgs[i];
        // Check if arg is a variable
        if (arg in this.variables) {
          args.push(this.variables[arg]);
        } 
        // If arg is not a variable, treat it as a literal value
        else {
          args.push(arg);
        }
      }
      return func.run(args);
    }
  }

  defineFunction(name, args, body) {
    this.functions[name] = {
      args: args,
      run: (passedArgs) => {
        let result;
        let argMap = {};
        for (let i = 0; i < args.length; i++) {
          argMap[args[i]] = passedArgs[i];
        }
        body.forEach((line) => {
          if (line.includes("=")) {
            let [varName, varValue] = line.split("=");
            this.variables[varName.trim()] = varValue.trim();
          } else if (line.includes("(")) {
            let functionName = line.split("(")[0].trim();
            let functionArgs = line.split("(")[1].split(")")[0].split(",").map(arg => argMap[arg.trim()] || arg.trim());
            if (this.functions[functionName] || this.builtInFunctions[functionName]) {
              result = this.runFunction(functionName, functionArgs);
            }
          }
        });
        return result;
      }
    };
  }
}

const parser = new Parser();

function parseFile(data) {
  data = data.split('\n').map(line => line.trim()).filter(line => line.length > 0);

  let i = 0;
  while (i < data.length) {
    let line = data[i];
    if (line.startsWith("func")) {
      let functionName = line.split("func")[1].split("(")[0].trim();
      let functionArgs = line.split("(")[1].split(")")[0].split(",").map(arg => arg.trim());
      let functionBody = [];
      i++;
      while (data[i] !== "}") {
        functionBody.push(data[i]);
        i++;
      }
      parser.defineFunction(functionName, functionArgs, functionBody);
    } else if (line.includes("=")) { // New
      let [varName, varValue] = line.split("=");
      parser.variables[varName.trim()] = varValue.trim();
    } else if (line.includes("(")) {
      let functionName = line.split("(")[0].trim();
      let functionArgs = line.split("(")[1].split(")")[0].split(",").map(arg => arg.trim());
      parser.runFunction(functionName, functionArgs);
    }
    i++;
  }
}

module.exports = parseFile;