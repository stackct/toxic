app.controller('SocketController', function($rootScope, $scope, $location, visibilityApiService) {

  $rootScope.latestProjects = [];
  $rootScope.environment = {};
  $rootScope.appVersion;
  $rootScope.lastUpdateData = null;
  $rootScope.shutdownWasPending = false;

  $scope.$on('visibilityChanged', function(event, isHidden) { 
    if (app.isVisible() && $rootScope.lastUpdateData != null) { 
      updateProjects($rootScope.lastUpdateData);
    } 
  });

  function startSocket(url, messageCallback) {
    var ws = new WebSocket(url);
    
    ws.onopen = function() {ws.send('init') };

    ws.onmessage = messageCallback;

    ws.onclose = function(event) {
      setTimeout(function() { startSocket(url, ws.onmessage); }, 2000); 
    };
  }

  function updateProjects(data) {
    $rootScope.latestProjects = data;
    $rootScope.latestGroups = [];
    $rootScope.latestProjectsByGroup = {};
    var idx;
    for (idx = 0; idx < $rootScope.latestProjects.length; idx++) {
      var group = $rootScope.latestProjects[idx].group;
      if (group.length == 0) group = "General";
      if ($rootScope.latestGroups.indexOf(group) == -1) {

        $rootScope.latestGroups.push(group);
      }
      var projects = $rootScope.latestProjectsByGroup[group];
      if (projects == null) {
        projects = [];
        $rootScope.latestProjectsByGroup[group] = projects;
      }
      projects.push($rootScope.latestProjects[idx]);
    }
  }

  startSocket(app.webSocketHost($location) + '/ws/projects', function(event) {

    var data = JSON.parse(event.data);

    if (app.isVisible()) {
      updateProjects(data);
    }
    $rootScope.lastUpdateData = data;

    var oldHealth = $rootScope.overallHealth;
    $rootScope.overallHealth = 'ok';

    var doomsday = new Date();
    doomsday.setDate(doomsday.getDate()-1); // yesterday

    for (var i=0; i<data.length; i++) {
      if ((data[i].failed > 0) && (data[i].group != 'Experiments')) {
        if ((Date.parse(data[i].startedDate) < doomsday)) {
          $rootScope.overallHealth = 'doom';
          break;
        } else {
          $rootScope.overallHealth = 'gloom';
        }
      }
    }

    if (oldHealth != $rootScope.overallHealth) {
      $rootScope.updateStamp = doomsday.getTime();
    }

    switch ($rootScope.overallHealth) {
      case "doom":
      case "gloom":
        $rootScope.bodyLayout = "skin-red";
        break;
      default:
        $rootScope.bodyLayout = "skin-green";
    }
    
    $rootScope.$apply();
  });

  startSocket(app.webSocketHost($location) + '/ws/environment', function(event) {
    var data = JSON.parse(event.data);

    data.heapUsedMb = data.heapUsed / 1000 / 1000;
    data.heapMaxMb  = data.heapMax / 1000 / 1000;
    data.heapPerc    = (data.heapUsed / data.heapMax) * 100;
    data.connected = true;

    $rootScope.environment = data;

    app.updateCharts(data.jobMetrics);

    if ($rootScope.appVersion == null) {
      $rootScope.appVersion = data.appVersion;
    }
    
    if (data.appVersion > $rootScope.appVersion) {
      $rootScope.appVersion = data.appVersion;
      $rootScope.$broadcast('setAlert', { msg:"UI was updated to " + data.appVersion });
      window.location.reload();
    }
    if (data.shutdownPending) {
      $rootScope.shutdownWasPending = true
      $rootScope.$broadcast('setWarning', { msg:"Server is waiting to restart. No new jobs will run at this time." });
    } else if ($rootScope.shutdownWasPending) {
      $rootScope.shutdownWasPending = false
      $rootScope.$broadcast('clearNotifications');
    }

    $rootScope.$apply();
  });

});
