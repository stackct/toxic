var app = angular.module('Toxic',
  [
    'ngRoute',
    'ngCookies',
    'ui.router',
    'ui.bootstrap',
    'angular.filter',
    'angular-duration-format',
    'cgBusy',
    'd3',
    'yaru22.md',
    'toxic.directives.d3'
]);

app.refreshDelaySeconds = 3;

app.apiHost = function apiHost(location) {
    var secure='';
    if(location.protocol() == 'https') {
        secure='s';
    }
    return 'http'+secure+'://' + location.host() + ':' + location.port();
};

app.webSocketHost = function webSocketHost(location) {
  var secure='';
  if(location.protocol() == 'https') {
    secure='s';
  }
  return 'ws'+secure+'://' + location.host() + ':' + location.port();
};

app.cancelRefresh = function(scope, interval) {
  interval.cancel(app.refreshPromise)
};

app.refreshIsolated = function(scope, interval, func, delay, count) {
  return interval(func, delay > 0 ? delay : 0, count > 0 ? count : 0);
};

app.refreshNow = function(scope, interval, func, delay) {
  app.refreshPromise = app.refreshIsolated(scope, interval, func, delay, 1);
};

app.refresh = function(scope, interval, func) {
  app.refreshing = (func == app.oldFunc);
  app.oldFunc = func;

  app.cancelRefresh(scope, interval);
  if (scope.shouldRefresh()) {
    app.refreshNow(scope, interval, func, app.refreshDelaySeconds * 1000);
  }
}

app.scrollToBottom = function(element) {
  window.setTimeout(function() {
    var scrollAmount = $(element).prop("scrollHeight");
    $(element).scrollTop(scrollAmount);
  }, 0);
};

app.isVisible = function() {
  var prop = null;
  if ('hidden' in document) {
    prop = 'hidden';
  } else {
    var prefixes = ['webkit','moz','ms','o'];
    for (var i = 0; i < prefixes.length; i++){
      if ((prefixes[i] + 'Hidden') in document)
        prop = prefixes[i] + 'Hidden';
    }
  }

  var visible = true;
  if (prop != null) {
    visible = !document[prop];
  }
  return visible;
};

app.service('visibilityApiService', function visibilityApiService($rootScope) {
  document.addEventListener("visibilitychange",visibilitychanged);
  document.addEventListener("webkitvisibilitychange", visibilitychanged);
  document.addEventListener("msvisibilitychange", visibilitychanged);

  function visibilitychanged() {
    $rootScope.$broadcast('visibilityChanged', document.hidden || document.webkitHidden || document.mozHidden || document.msHidden)
  }
});

///////////// CHARTS //////////////////

app.lookupTimechart = function(force) {
  if (app.timechart == null || force) {
    var canvas = document.getElementById("timechart")
    if (canvas != null) {
      var ctx = canvas.getContext('2d');
      app.timechart = new Chart(ctx, {
        type: 'line',
        data: {
          datasets: [{
            label: "Failures",
            backgroundColor:  Chart.helpers.color('#d33724').alpha(0.5).rgbString(),
            borderColor: '#d33724',
            fill: false,
            data: [],
          }, {
            label: "Avg Duration",
            backgroundColor:  Chart.helpers.color('#3c8dbc').alpha(0.5).rgbString(),
            borderColor: '#3c8dbc',
            fill: false,
            data: [],
          }, {
            label: "Jobs",
            backgroundColor:  Chart.helpers.color('#00a65a').alpha(0.5).rgbString(),
            borderColor: '#00a65a',
            fill: false,
            data: [],
          }]
        },
        options: {
          scales: {
            xAxes: [{
              type: "time",
              time: {
                format: 'YYYY-MM-DD',
                // round: 'day'
                tooltipFormat: 'll'
              },
            }, ],
            yAxes: [{
              ticks: {
                min: 0
              }
            }],
          },
        }
      });
    }
  }
  return app.timechart;
}

app.updateTimechart = function(data, force) {
  var failedData = [];
  var durationData = [];
  var totalData = [];

  if (data != null && data.dailyMetrics != null) {
    data.dailyMetrics.forEach( function (dailyDetails) {
      failedData.push({x:dailyDetails.date,y:dailyDetails.failedJobs});
      durationData.push({x:dailyDetails.date,y:dailyDetails.avgDuration});
      totalData.push({x:dailyDetails.date,y:dailyDetails.totalJobs});
    });
  }

  var timechart = app.lookupTimechart(force);
  if (timechart != null) {
    timechart.data.datasets[0].data = failedData;
    timechart.data.datasets[1].data = durationData;
    timechart.data.datasets[2].data = totalData;
    timechart.update();
  }
}

app.lookupTopDuration = function(force) {
  if (app.topDuration == null || force) {
    var canvas = document.getElementById("topDuration")
    if (canvas != null) {
      var ctx = canvas.getContext('2d');
      app.topDuration = new Chart(ctx, {
        type: 'horizontalBar',
        data: {
          labels: [],
          datasets: [{
            label: "Avg Job Duration (minutes)",
            backgroundColor:  Chart.helpers.color('#3c8dbc').alpha(0.5).rgbString(),
            borderColor: '#3c8dbc',
            borderWidth: 1,
            data: []
          }]
        },
        options: {
          scales: {
            xAxes: [{
              ticks: {
                min: 0
              }
            }],
            yAxes: [{
            }]
          }
        }
      });
    }
  }
  return app.topDuration;
}

app.topDurationLimit = '10';
app.updateTopDuration = function(data, force) {
  var totalData = [];
  var nameData = [];

  if (data != null && data.projectMetrics != null) {
    var count = 0;
    var flat = []
    for (var key in data.projectMetrics) {
      flat.push([key, data.projectMetrics[key]]);
    };

    flat.sort(function(a, b) {
      return a[1].avgDuration > b[1].avgDuration ? -1 : a[1].avgDuration < b[1].avgDuration ? 1 : 0;
    });
    flat.forEach( function(record) {
      if (count < parseInt(app.topDurationLimit)) {
        nameData.push(record[0]);
        totalData.push(record[1].avgDuration);
      }
      count++;
    });
  }

  var chart = app.lookupTopDuration(force);
  if (chart != null) {
    chart.data.datasets[0].data = totalData;
    chart.data.labels = nameData;
    chart.update();
  }
}

app.lookupTopCommitters = function(force) {
  if (app.topCommitters == null || force) {
    var canvas = document.getElementById("topCommitters")
    if (canvas != null) {
      var ctx = canvas.getContext('2d');
      app.topCommitters = new Chart(ctx, {
        type: 'horizontalBar',
        data: {
          labels: [],
          datasets: [{
            label: "Successful Commits",
            backgroundColor:  Chart.helpers.color('#00a65a').alpha(0.5).rgbString(),
            borderColor: '#00a65a',
            borderWidth: 1,
            data: []
          }]
        },
        options: {
          legend: {
            display: false
          },
          scales: {
            xAxes: [{
              stacked: true,
              ticks: {
                min: 0
              }
            }],
            yAxes: [{
              stacked: true
            }]
          }
        }
      });
    }
  }
  return app.topCommitters;
}

app.topCommittersLimit = '20';
app.updateTopCommitters = function(data, force) {
  var totalData = [];
  var nameData = [];

  if (data != null && data.commitMetrics != null) {
    var count = 0;
    var flat = []
    for (var key in data.commitMetrics) {
      flat.push([key, data.commitMetrics[key]]);
    };

    flat.sort(function(a, b) {
      return a[1].totalCommits > b[1].totalCommits ? -1 : a[1].totalCommits < b[1].totalCommits ? 1 : 0;
    });
    flat.forEach( function(record) {
      if (count < parseInt(app.topCommittersLimit)) {
        nameData.push(record[0]);
        totalData.push(record[1].totalCommits);
      }
      count++;
    });
  }

  var chart = app.lookupTopCommitters(force);
  if (chart != null) {
    chart.data.datasets[0].data = totalData;
    chart.data.labels = nameData;
    chart.update();
  }
}





app.lookupWallOfShame = function(force) {
  if (app.wallOfShame == null || force) {
    var canvas = document.getElementById("wallOfShame")
    if (canvas != null) {
      var ctx = canvas.getContext('2d');
      app.wallOfShame = new Chart(ctx, {
        type: 'horizontalBar',
        data: {
          labels: [],
          datasets: [{
            label: "Blamed Commits",
            backgroundColor:  Chart.helpers.color('#d33724').alpha(0.5).rgbString(),
            borderColor: '#d33724',
            borderWidth: 1,
            data: []
          }]
        },
        options: {
          legend: {
            display: false
          },
          scales: {
            xAxes: [{
              stacked: true,
              ticks: {
                min: 0
              }
            }],
            yAxes: [{
              stacked: true
            }]
          }
        }
      });
    }
  }
  return app.wallOfShame;
}

app.wallOfShameLimit = '20';
app.updateWallOfShame = function(data, force) {
  var failureData = [];
  var nameData = [];

  if (data != null && data.commitMetrics != null) {
    var count = 0;
    var flat = []
    for (var key in data.commitMetrics) {
      flat.push([key, data.commitMetrics[key]]);
    };

    flat.sort(function(a, b) {
      return a[1].failedJobs > b[1].failedJobs ? -1 : a[1].failedJobs < b[1].failedJobs ? 1 : 0;
    });
    flat.forEach( function(record) {
      if (count < parseInt(app.wallOfShameLimit)) {
        nameData.push(record[0]);
        failureData.push(record[1].failedJobs);
      }
      count++;
    });
  }

  var chart = app.lookupWallOfShame(force);
  if (chart != null) {
    chart.data.datasets[0].data = failureData;
    chart.data.labels = nameData;
    chart.update();
  }
}







app.lookupTopProjects = function(force) {
  if (app.topProjects == null || force) {
    var canvas = document.getElementById("topProjects")
    if (canvas != null) {
      var ctx = canvas.getContext('2d');
      app.topProjects = new Chart(ctx, {
        type: 'horizontalBar',
        data: {
          labels: [],
          datasets: [{
            label: "Failures",
            backgroundColor:  Chart.helpers.color('#d33724').alpha(0.5).rgbString(),
            borderColor: '#d33724',
            borderWidth: 1,
            data: []
          },
          {
            label: "Successes",
            backgroundColor:  Chart.helpers.color('#00a65a').alpha(0.5).rgbString(),
            borderColor: '#00a65a',
            borderWidth: 1,
            data: []
          }]
        },
        options: {
          scales: {
            xAxes: [{
              stacked: true,
              ticks: {
                min: 0
              }
            }],
            yAxes: [{
              stacked: true
            }]
          }
        }
      });
    }
  }
  return app.topProjects;
}

app.topProjectsLimit = '10';
app.updateTopProjects = function(data, force) {
  var totalData = [];
  var failureData = [];
  var nameData = [];

  if (data != null && data.projectMetrics != null) {
    var count = 0;
    var flat = []
    for (var key in data.projectMetrics) {
      flat.push([key, data.projectMetrics[key]]);
    };

    flat.sort(function(a, b) {
      return a[1].totalJobs > b[1].totalJobs ? -1 : a[1].totalJobs < b[1].totalJobs ? 1 : 0;
    });
    flat.forEach( function(record) {
      if (count < parseInt(app.topProjectsLimit)) {
        nameData.push(record[0]);
        totalData.push(record[1].totalJobs-record[1].failedJobs);
        failureData.push(record[1].failedJobs);
      }
      count++;
    });
  }

  var chart = app.lookupTopProjects(force);
  if (chart != null) {
    chart.data.datasets[0].data = failureData;
    chart.data.datasets[1].data = totalData;
    chart.data.labels = nameData;
    chart.update();
  }
}

app.updateCharts = function(data, force) {
  if (data == null) {
    data = app.cachedChartData;
  }
  app.updateTimechart(data, force);
  app.updateTopDuration(data, force);
  app.updateTopCommitters(data, force);
  app.updateWallOfShame(data, force);
  app.updateTopProjects(data, force);
  app.cachedChartData = data;
}
