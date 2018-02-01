app.controller('JobDetailController', function($rootScope, $scope, $http, $stateParams, $interval, $location) {
  $rootScope.page = 'Jobs';
  $scope.sortField = "success";
  $scope.sortReverse = false;
  $scope.modalSortField = "name";
  $scope.modalSortReverse = false;
  $scope.artifactsSortField = "name";
  $scope.artifactsSortReverse = false;
  $scope.filter = { status: "failed" };
  $scope.Math = window.Math;

  $scope.disableScroll = function() {
    //$('html').addClass('fixed-body')
  }

  $scope.enableScroll = function() {
    //$('html').removeClass('fixed-body')
  }
  
  $scope.busy = function() { 
    return !app.refreshing ? $scope.promise : null 
  }

  $scope.shouldRefresh = function() {
    // no need to refresh if job is already stopped
    return ['RUNNING','INITIALIZING','PENDING'].indexOf($scope.job.status) != -1
  }

  $scope.loadSuiteDetail = function(suite) {
    $scope.suiteDetail = {};
    $scope.suiteDetailPromise = $http.get(app.apiHost($location) + '/api/job/' + $stateParams.id + '/suite/' + suite);
    $scope.suiteDetailPromise
      .success(function(data) {
        $scope.suiteDetail = {'name': suite, 'tasks': data}
      })
      .error(function(data, error, status, headers, config) {
      });
  };

  $scope.loadChangeset = function(id) {
    $scope.changeset = {};
    $scope.changesetPromise = $http.get(app.apiHost($location) + '/api/job/' + $stateParams.id + '/changeset/' + id);
    $scope.changesetPromise
      .success(function(data) {
        $scope.changeset = {'id': id, 'details': data.details}
      })
      .error(function(data, error, status, headers, config) {
      });
  };

  $scope.loadSummary = function() {
    app.cancelRefresh($scope, $interval);
    $scope.promise = $http.get(app.apiHost($location) + '/api/job/' + $stateParams.id);
    $scope.promise
      .success(function(data) {
        $scope.job = data;
        app.refresh($scope, $interval, $scope.loadSummary)
      })
      .error(function(data, status, headers, config) {
        app.refresh($scope, $interval, $scope.loadSummary)
      });
  };

  $scope.loadTaskResultsTab = function() {
    if(!$scope.resultsDisplay) {
      $scope.resultsDisplay = 'list';
    }
    if($scope.resultsDisplay == "list") {
      $scope.loadDetails();
    } else {
      $scope.loadTaskResults();
    }
  }

  $scope.loadDetails = function() {
    app.cancelRefresh($scope, $interval);
    var bookmark = $scope.suitesBookmark;
    if (bookmark == null) bookmark = 0;
    $scope.promise = $http.get(app.apiHost($location) + '/api/job/' + $stateParams.id + '/suites/' + bookmark
        + "?statusFilter=" + $scope.filter.status);
    $scope.promise
      .success(function(data) {
        if (data != null) {
          $scope.job = data.job;
          
          if (data.suites != null && data.suites.length > 0) {
            if ($scope.suites == null) {
              $scope.suites = data.suites;
            } else {
              // May need to merge the first returned suite if it is a continuation
              // of the last suite in the previously fetched set of suites.
              var lastSuite = $scope.suites[$scope.suites.length-1];
              var firstSuite = data.suites[0];
              if (lastSuite.suite == firstSuite.suite) {
                data.suites.shift();
                lastSuite.tasks += firstSuite.tasks;
                lastSuite.complete += firstSuite.complete;
                lastSuite.success += firstSuite.success;
                lastSuite.duration += firstSuite.duration;
              }
              $scope.suites = $scope.suites.concat(data.suites);
            }
            $scope.suitesBookmark = data.bookmark;
          }
        }
        app.refresh($scope, $interval, $scope.loadDetails)
      })
      .error(function(data, status, headers, config) {
        app.refresh($scope, $interval, $scope.loadDetails)
      });
  };

  $scope.reloadDetails = function() {
    $scope.suitesBookmark = 0;
    $scope.suites = null;
    $scope.loadDetails();
  }

  $scope.logOptions = [
    { name: "top of file", value: 0 },
    { name: "bottom of file", value: 1 }
  ];

  $scope.loadLog = function() {
    app.cancelRefresh($scope, $interval);
    var headOrTail = ($scope.log_tab.tail.value == 1) ? "/logTail/" : "/logHead/"
    var logUrl = app.apiHost($location) + '/api/job/' + $stateParams.id + headOrTail + $scope.log_tab.num_lines
    $scope.promise = $http.get(logUrl);
    $scope.promise
      .success(function(data) {
        if (data != null) {
          $scope.job = data.job;
          $scope.log_tab.unfiltered = data.log;
          $scope.log_tab.log = data.log;
          $scope.applyLogFilter($scope.log_tab.unfiltered, false);
          if($scope.log_tab.pin_to_bottom && $scope.log_tab.tail.value == 1) {
            app.scrollToBottom("#log");
          }
        }
        if($scope.log_tab.auto_refresh) {
          app.refresh($scope, $interval, $scope.loadLog);
        }
      })
      .error(function() {
        if($scope.log_tab.auto_refresh) {
          app.refresh($scope, $interval, $scope.loadLog);
        }
      });
  };
  
  $scope.loadArtifacts = function() {
    app.cancelRefresh($scope, $interval);
    $scope.promise = $http.get(app.apiHost($location) + '/api/job/' + $stateParams.id + '/artifacts');
    $scope.promise
      .success(function(data) {
        if (data != null) {
          $scope.job = data.job;
          $scope.artifacts = data.artifacts;
        }
        app.refresh($scope, $interval, $scope.loadArtifacts)
      })
      .error(function(data, status, headers, config) {
        app.refresh($scope, $interval, $scope.loadArtifacts)
      });
  };

  $scope.loadTaskResults = function() {
    $scope.promise = $http.get(app.apiHost($location) + '/api/job/' + $stateParams.id + '/tasks');
    $scope.promise
      .success(function(data) {
        if (data != null) {
          $scope.taskResults = data;
        }
      })
      .error(function(data, status, headers, config) {
        console.log("Error getting task results: "+data)
      });
  };

  $scope.loadDocs = function() {
    $scope.promise = $http.get(app.apiHost($location) + '/api/job/' + $stateParams.id + '/docs');
    $scope.promise
      .success(function(data) {
        if (data != null) {
          $scope.docs = data;
        }
      })
      .error(function(data, status, headers, config) {
        console.log("Error getting docs: "+data)
      });
  };

  $scope.loadImages = function() {
    $scope.promise = $http.get(app.apiHost($location) + '/api/job/' + $stateParams.id + '/images');
    $scope.promise
      .success(function(data) {
        $scope.images = { };
        if (data) {
          $scope.images = data;
        }
      })
      .error(function(data, status, headers, config) {
        console.log("Error getting images: "+data)
      });
  };

  $scope.keys = function(obj) {
    return obj? Object.keys(obj) : [];
  }

  $scope.applyLogFilter = function(input, append) {
    $scope.log_tab.filter = $(logFilter)[0].value;
    if (!append) $scope.log_tab.log = "";
    var filtered = app.regexFilter(input, $scope.log_tab.filter);
    $scope.log_tab.log += filtered;
    $scope.updateLogFilter();
  }
  
  $scope.updateLogFilter = function() {
    newFilter = $(logFilter)[0].value;
  }

  $scope.clearLogFilter = function() {
    $(logFilter)[0].value = '';
    $scope.applyLogFilter($scope.log_tab.unfiltered, false);
  }

  $scope.updateLog = function() {
    $scope.loadLog();
  }
  
  $scope.stopJob = function() {
    $rootScope.$broadcast('setAlert', {msg:"Stopping job ..."});
    $http.get(app.apiHost($location) + '/api/job/' + $stateParams.id + '/halt');
  }

  $scope.startJob = function() {
    $scope.promise = $http.get(app.apiHost($location) + '/api/job/' + $stateParams.id + '/start');
    $scope.promise.success(function(data) { 
      if (data.jobId != null) {
        $location.path('job/' + data.jobId);
      } else {
        $rootScope.$broadcast('setError', {msg:"Job cannot be started at this time."});
      }
    });
  }

  $scope.ackJob = function() {
    $scope.promise = $http.get(app.apiHost($location) + '/api/job/' + $stateParams.id + '/ack/' + $rootScope.user.id);
    $scope.promise.success(function(data) { 
      if (!data.result) {
        $rootScope.$broadcast('setError', {msg: "Could not ack job '" + $stateParams.id + "'"});
        return;
      }
      
      $scope.loadDetails()
    });
    $scope.promise.error(function(data) {
      $rootScope.$broadcast('setError', {msg: "Could not ack job '" + $stateParams.id + "'"});
    });
  };
  
  $scope.resolveJob = function() {
    $scope.promise = $http.get(app.apiHost($location) + '/api/job/' + $stateParams.id + '/resolve/' + $rootScope.user.id);
    $scope.promise.success(function(data) { 
      if (!data.result) {
        $rootScope.$broadcast('setError', {msg: "Could not resolve job '" + $stateParams.id + "'"});
        return;
      }
      
      $scope.loadDetails()
    });
    $scope.promise.error(function(data) {
      $rootScope.$broadcast('setError', {msg: "Could not resolve job. Reason: " + data});
    });
  };

  $scope.performAction = function(key) {
    var action = $scope.job.actions[key];
    $rootScope.$broadcast('setAlert', {msg:"Performing " + action.name + "..."});
    $scope.promise = $http.get(app.apiHost($location) + '/api/job/' + $stateParams.id + '/action/' + key);
    $scope.promise.success(function(data) {
      if (data == "0") {
        $rootScope.$broadcast('setAlert', {msg:action.name + " completed with successful result (" + data + ")"});
      } else {
        $rootScope.$broadcast('setError', {msg:action.name + " completed with failed result (" + data + ")"});
      }
    });
    $scope.promise.error(function(data) {
      $rootScope.$broadcast('setError', {msg:action.name + " was not performed; verify you are authenticated and that you have access to this action"});
    });
  }

  $scope.log_tab = {
    num_lines: 2000,
    tail: 1,
    auto_refresh: true,
    pin_to_bottom: true
  }

  $rootScope.$broadcast('clearNotifications', {});
  $scope.loadSummary();
});
