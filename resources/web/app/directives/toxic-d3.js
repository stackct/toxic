// See http://www.ng-newsletter.com/posts/d3-on-angular.html
// and http://bost.ocks.org/mike/treemap/

angular.module('d3', [])
  .factory('d3', ['$document', '$q', '$rootScope',
    function($document, $q, $rootScope, $window) {
      var d = $q.defer();
      function onScriptLoad() {
        // Load client in the browser
        $rootScope.$apply(function() { d.resolve(window.d3); });
      }
      // Create a script tag with d3 as the source
      // and call our onScriptLoad callback when it
      // has been loaded
      var scriptTag = $document[0].createElement('script');
      scriptTag.type = 'text/javascript';
      scriptTag.async = true;
      scriptTag.src = '/assets/lib/d3.v3.min.js';
      scriptTag.onreadystatechange = function () {
        if (this.readyState == 'complete') onScriptLoad();
      }
      scriptTag.onload = onScriptLoad;

      var s = $document[0].getElementsByTagName('body')[0];
      s.appendChild(scriptTag);

      return {
        d3: function() { return d.promise; }
      };
    }]);


angular.module('toxic.directives.d3', [])
  .directive('resultsTreemap', ['d3', '$window', '$rootScope', function(d3Service, $window, $rootScope) {
    return {
      restrict: 'EA',
      scope: {
        jobId: '=',
        data: '='
      },
      link: function(scope, element, attrs) {
        d3Service.d3().then(function(d3) {
          var margin = {top: 40, right: 0, bottom: 0, left: 0},
            width = parseInt(attrs.width) || 960,
            height = (parseInt(attrs.height) || 500) - margin.top - margin.bottom,
            formatNumber = d3.format(",.1f"),
            transitioning;

          var taskData;
          // Watch for data change
          scope.$watch("data", function(newVal) {
            taskData = newVal
            scope.render(scope.jobId, taskData, svg);
          });

          var svgParent = d3.select(element[0]).append("svg")
            .attr("id","results-treemap")
            .attr("width", width + margin.left + margin.right)
            .attr("height", height + margin.bottom + margin.top)
            .style("margin-left", -margin.left + "px")
            .style("margin.right", -margin.right + "px")
          var svg = svgParent.append("g")
            .attr("transform", "translate(" + margin.left + "," + margin.top + ")")
            .style("shape-rendering", "crispEdges");

          scope.render = function(jobId, data, svg) {
            svg.selectAll('*').remove();

            // If we don't pass any data, return out of the element
            if (!data) return;

            //console.log("setting width to "+width)
            //svgParent.attr("width", width)

            var x = d3.scale.linear()
              .domain([0, width])
              .range([0, width]);

            var y = d3.scale.linear()
              .domain([0, height])
              .range([0, height]);

            var treemap = d3.layout.treemap()
              .children(function(d, depth) {
                return depth ? null : d._children;
              })
              .sort(function(a, b) {
                return a.value - b.value;
              })
              .ratio(height / width * 0.5 * (1 + Math.sqrt(5)))
              .round(false)

            var grandparent = svg.append("g")
              .attr("class", "grandparent");

            grandparent.append("rect")
              .attr("y", -margin.top)
              .attr("width", width)
              .attr("height", margin.top);

            grandparent.append("text")
              .attr("x", 6)
              .attr("y", 6 - margin.top)
              .attr("dy", ".75em");

            renderChart(data)
            function renderChart (root) {
              initialize(root);
              accumulate(root);
              layout(root);
              display(root);

              function initialize(root) {
                root.x = root.y = 0;
                root.dx = width;
                root.dy = height;
                root.depth = 0;
              }

              // Aggregate the values for internal nodes. This is normally done by the
              // treemap layout, but not here because of our custom implementation.
              // We also take a snapshot of the original children (_children) to avoid
              // the children being overwritten when when layout is computed.
              function accumulate(d) {
                var valueAcc = 0
                var hasFailure = false;
                if(d._children = d.children) {
                  var reduction = d.children.reduce(function(p, v) {
                    var pVal = p[0]
                    var pFail = p[1]
                    var childReduction = accumulate(v)
                    var cVal = childReduction[0]
                    var cFail = childReduction[1]
                    return [pVal+cVal, pFail || cFail]
                  }, [0, false])
                  d.value = reduction[0];
                  d.duration = reduction[0];
                  d.hasFailure = reduction[1];
                  valueAcc = d.value
                  hasFailure = d.hasFailure
                } else {
                  d.value = d.duration
                  valueAcc = d.value
                  hasFailure = !d.success
                  d.hasFailure = !d.success
                }
                return [valueAcc, hasFailure]
              }

              function layout(d) {
                if (d._children) {
                  treemap.nodes({_children: d._children});
                  d._children.forEach(function(c) {
                    c.x = d.x + c.x * d.dx;
                    c.y = d.y + c.y * d.dy;
                    c.dx *= d.dx;
                    c.dy *= d.dy;
                    c.parent = d;
                    layout(c);
                  });
                }
              }

              var childCount=0;
              function display(d) {
                childCount++;
                grandparent
                  .datum(d.parent)
                  .on("click", transition)
                  .select("text")
                  .text(name(d));

                var g1 = svg.insert("g", ".grandparent")
                  .datum(d)
                  .attr("class", "depth");

                var g = g1.selectAll("g")
                  .data(d._children)
                  .enter().append("g");

                g.filter(function(d) { return d._children; })
                  .classed("children", true)
                  .on("click", transition);

                g.selectAll(".child")
                  .data(function(d) { return d._children || [d]; })
                  .enter().append("rect")
                  .attr("class", "child")
                  .call(rect);

                // Mark all leaf nodes (no children)
                g.filter(function(d) { return !d._children; })
                  .classed("leaf", true)

                // Mark all nodes that contain a failed test
                g.filter(function(d) { return d.hasFailure; })
                  .classed("hasFailure", true)

                // All the leaf nodes with failures should link to a modal containing the failure message
                g.filter(function(d) {return !d._children && d.hasFailure})
                  .attr("data-toggle", "modal").attr("data-target","#taskFailureModal")
                  .on("click", function(d) {
                    $rootScope.taskToDisplay= d;
                    scope.$apply()
                    //showError(d);
                  })


                g.append("rect")
                  .attr("class", "parent")
                  .call(rect)
                  .append("title").text(function(d) { return getText(d); })

                var clipPath =g.append("clipPath").attr("id", function(d){return "clip-"+ d.id;});
                clipPath.append("rect").call(rect);

                g.append("text")
                  .attr("clip-path", function(d) { return "url(#clip-"+ d.id+")"} )
                  .attr("dy", ".75em")
                  .text(function(d) { return getText(d); })
                  .call(text);

                function getText(d) {
                  if(!d) { return "not d"; }
                  var t = d.name
                  if(d.duration) {
                    t = t + " ("+formatNumber(d.duration/1000)+"s)"
                  }
                  return t;
                }

                function showError(d) {
                  console.log("Error: "+ d.error)
                }

                function transition(d) {
                  if (transitioning || !d) return;
                  transitioning = true;

                  var g2 = display(d),
                    t1 = g1.transition().duration(750),
                    t2 = g2.transition().duration(750);

                  // Update the domain only after entering new elements.
                  x.domain([d.x, d.x + d.dx]);
                  y.domain([d.y, d.y + d.dy]);

                  // Enable anti-aliasing during the transition.
                  svg.style("shape-rendering", null);

                  // Draw child nodes on top of parent nodes.
                  svg.selectAll(".depth").sort(function(a, b) { return a.depth - b.depth; });

                  // Fade-in entering text.
                  g2.selectAll("text").style("fill-opacity", 0);

                  // Transition to the new view.
                  t1.selectAll("text").call(text).style("fill-opacity", 0);
                  t2.selectAll("text").call(text).style("fill-opacity", 1);
                  t1.selectAll("rect").call(rect);
                  t2.selectAll("rect").call(rect);

                  // Remove the old node when the transition is finished.
                  t1.remove().each("end", function() {
                    svg.style("shape-rendering", "crispEdges");
                    transitioning = false;
                  });
                }

                return g;
              }

              function text(text) {
                text.attr("x", function(d) { return x(d.x) + 6; })
                  .attr("y", function(d) { return y(d.y) + 6; });
              }

              function rect(rect) {
                rect.attr("x", function(d) { return x(d.x); })
                  .attr("y", function(d) { return y(d.y); })
                  .attr("width", function(d) { return x(d.x + d.dx) - x(d.x); })
                  .attr("height", function(d) { return y(d.y + d.dy) - y(d.y); });
              }

              function name(d) {
                var n =  d.parent
                  ? name(d.parent) + "/" + d.name
                  : d.name;
                return n.replace(/^root/,"")
              }
            }
          }

        });
      }};
  }]);
