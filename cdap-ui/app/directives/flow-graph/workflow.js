/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

var module = angular.module(PKG.name+'.commons');

var baseDirective = {
  restrict: 'E',
  templateUrl: 'flow-graph/flow.html',
  scope: {
    model: '=',
    onChangeFlag: '=',
    clickContext: '=',
    click: '&',
    tokenClick: '&',
    workflowStatus: '='
  },
  controller: 'myFlowController'
};


module.directive('myWorkflowGraph', function ($filter, $location, FlowFactories, $tooltip) {
  return angular.extend({
    link: function (scope) {
      scope.render = FlowFactories.genericRender.bind(null, scope, $filter, $location, true);

      var defaultRadius = 30;
      scope.getShapes = function() {
        var shapes = {};
        shapes.job = function(parent, bbox, node) {

          // Creating Hexagon
          var xPoint = defaultRadius * 7/8;
          var yPoint = defaultRadius * 1/2;
          var points = [
            // points are listed from top and going clockwise
            { x: 0, y: defaultRadius},
            { x: xPoint, y: yPoint},
            { x: xPoint, y: -yPoint },
            { x: 0, y: -defaultRadius},
            { x: -xPoint, y: -yPoint},
            { x: -xPoint, y: yPoint}
          ];

          parent.select('.label')
            .attr('transform', 'translate(0,'+ (defaultRadius + 20) + ')')
            .attr('class', 'node-label');

          var shapeSvg = parent.insert('polygon', ':first-child')
              .attr('points', points.map(function(p) { return p.x + ',' + p.y; }).join(' '))
              .attr('id', 'job-' + scope.instanceMap[node.labelId].nodeId)
              .attr('class', 'workflow-shapes foundation-shape job-svg');

          // WORKFLOW TOKENS
          // It will default render with class hidden
          var token = parent.append('g')
            .attr('transform', 'translate(0, ' + (-defaultRadius - 25) + ')' )
            .attr('class', 'token hidden')
            .attr('id', 'token-' + scope.instanceMap[node.labelId].nodeId);

          token.append('circle')
            .attr('r', 11)
            .attr('class', 'workflow-token');
          token.append('text')
            .text('T')
            .attr('x', -4)
            .attr('y', 5)
            .attr('class', 'token-label');


          node.intersect = function(point) {
            return dagreD3.intersect.polygon(node, points, point);
          };

          return shapeSvg;
        };

        shapes.start = function(parent, bbox, node) {
          var w = bbox.width;
          var points = [
            // draw a triangle facing right
            { x: -20, y: -30},
            { x: 20, y: 0},
            { x: -20, y: 30},
          ];

          parent.select('.label')
            .attr('transform', 'translate(0,'+ (bbox.height + 20) + ')');

          var shapeSvg = parent.insert('polygon', ':first-child')
            .attr('points', points.map(function(p) { return p.x + ',' + p.y; }).join(' '))
            .attr('transform', 'translate(' + (w/6) + ')')
            .attr('class', 'workflow-shapes foundation-shape start-svg');

          node.intersect = function(point) {
            return dagreD3.intersect.polygon(node, points, point);
          };

          return shapeSvg;
        };

        shapes.end = function(parent, bbox, node) {
          var w = bbox.width;
          var points = [
            // draw a triangle facing right
            { x: -20, y: 0},
            { x: 20, y: 30},
            { x: 20, y: -30},
          ];

          parent.select('.label')
            .attr('transform', 'translate(0,'+ (bbox.height + 20) + ')');

          var shapeSvg = parent.insert('polygon', ':first-child')
            .attr('points', points.map(function(p) { return p.x + ',' + p.y; }).join(' '))
            .attr('transform', 'translate(' + (-w/6) + ')')
            .attr('class', 'workflow-shapes foundation-shape end-svg');

          node.intersect = function(point) {
            return dagreD3.intersect.polygon(node, points, point);
          };

          return shapeSvg;
        };

        shapes.conditional = function(parent, bbox, node) {
          var points = [
            // draw a diamond
            { x:  0, y: -defaultRadius*3/4 },
            { x: -defaultRadius*3/4, y:  0 },
            { x:  0, y:  defaultRadius*3/4 },
            { x:  defaultRadius*3/4, y:  0 }
          ];

          parent.select('.label')
            .attr('transform', 'translate(0,'+ (bbox.height + 20) + ')');

          var shapeSvg = parent.insert('polygon', ':first-child')
            .attr('points', points.map(function(p) { return p.x + ',' + p.y; }).join(' '))
            .attr('class', 'workflow-shapes foundation-shape conditional-svg');

          if (node.label === 'IF') {
            parent.attr('class', 'if-condition');

            var token = parent.append('g')
              .attr('transform', 'translate(0, ' + (-defaultRadius - 25) + ')' )
              .attr('class', 'token hidden')
              .attr('id', 'token-' + scope.instanceMap[node.labelId].nodeId);

            token.append('circle')
              .attr('r', 11)
              .attr('class', 'workflow-token');
            token.append('text')
              .text('T')
              .attr('x', -4)
              .attr('y', 5)
              .attr('class', 'token-label');
          }

          node.intersect = function(p) {
            return dagreD3.intersect.polygon(node, points, p);
          };
          return shapeSvg;
        };

        shapes.forkjoin = function(parent, bbox, node) {

          parent.select('.label')
            .attr('style', 'display: none;');

          var shapeSvg = parent.insert('circle', ':first-child')
            .attr('x', -bbox.width / 2)
            .attr('y', -bbox.height / 2)
            .attr('r', 0);


          node.intersect = function(p) {
            return dagreD3.intersect.circle(node, 1, p);
          };

          return shapeSvg;
        };

        return shapes;
      };

      scope.getShape = function(name) {
        var shapeName;

        switch(name) {
          case 'ACTION':
            shapeName = 'job';
            break;
          case 'FORKNODE':
            shapeName = 'forkjoin';
            break;
          case 'JOINNODE':
            shapeName = 'forkjoin';
            break;
          case 'START':
            shapeName = 'start';
            break;
          case 'END':
            shapeName = 'end';
            break;
          default:
            shapeName = 'conditional';
            break;
        }
        return shapeName;
      };

      scope.handleNodeClick = function(nodeId) {
        var instance = scope.instanceMap[nodeId];
        scope.$apply(function(scope) {
          var fn = scope.click();
          if ('undefined' !== typeof fn) {
            fn.call(scope.clickContext, instance);
          }
        });
      };

      scope.toggleToken = function (nodeId) {
        var node = scope.instanceMap[nodeId];

        scope.$apply(function(scope) {
          var fn = scope.tokenClick();
          if ('undefined' !== typeof fn) {
            fn.call(scope.clickContext, node);
          }
        });
      };

      scope.arrowheadRule = function(edge) {
        if (edge.targetType === 'JOINNODE' || edge.targetType === 'FORKNODE') {
          return false;
        } else {
          return true;
        }
      };

      scope.update = function () {
        angular.forEach(scope.model.current, function (value, key) {
          var instanceId = scope.instanceMap[key].nodeId;
          var shapeSvg = scope.svg.select('#job-' + instanceId);

          switch(value) {
            case 'COMPLETED':
              shapeSvg.attr('class', 'workflow-shapes foundation-shape job-svg completed');

              // Token icon should only show when status is COMPLETED
              scope.svg.select('#token-' + instanceId)
                .attr('class', 'token');

              break;
            case 'RUNNING':
              shapeSvg.attr('class', 'workflow-shapes foundation-shape job-svg running');
              break;
            case 'FAILED':
              shapeSvg.attr('class', 'workflow-shapes foundation-shape job-svg failed');
              break;
            case 'KILLED':
              shapeSvg.attr('class', 'workflow-shapes foundation-shape job-svg killed');
              break;
            default:
              shapeSvg.attr('class', 'workflow-shapes foundation-shape job-svg');
          }

        });

        if (scope.workflowStatus === 'COMPLETED' || scope.workflowStatus === 'FAILED') {
          var ifNodes = scope.svg.selectAll('.if-condition')[0];
          angular.forEach(ifNodes, function (ifNode) {
            d3.select(ifNode).select('.token')
              .attr('class', 'token');
          });
        }

      };

      scope.createTooltips = function () {
        var labels = d3.selectAll('.node-label')[0];
        var labelTooltips = {};

        angular.forEach(labels, function (label) {
          var text = scope.instanceMap[label.id].program.programType || scope.instanceMap[label.id].program.programName;

          labelTooltips[label.id] = $tooltip(angular.element(label), {
            title: scope.instanceMap[label.id].nodeId + ' : ' + text,
            trigger: 'manual',
            delay: { show: 300, hide: 0 },
            target: angular.element(label),
            container: '.diagram-container'
          });
        });

        d3.selectAll('.node-label')
          .on('mouseover', function (node) {
            labelTooltips[node].enter();
          })
          .on('mouseout', function (node) {
            labelTooltips[node].leave();
          });
      };


      FlowFactories.prepareGraph(scope);

    }
  }, baseDirective);
});
