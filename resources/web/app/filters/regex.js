app.regexFilter = function(input, exp) {
  if (input == null || exp == "") return input;
    
  if (exp.charAt(0) != "/") {
    exp = ".*" + exp + ".*"
  }
  var regex = new RegExp(exp, "g")
  var matches = input.match(regex);
  if (matches == null) return "";
  return matches.join("\n");
}
