// HTML5 charts package
/*
Copyright 2015 ben+dev@amb1ent.org

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

(function(window, document, $) {'use strict';

  // bootstrap the global object fabric.charts
  function ensure(obj, name, factory) {
    return obj[name] || (obj[name] = factory());
  }
  var fabric = ensure(window, 'fabric', Object);
  var charts = ensure(fabric, 'charts', Object);

  // factory to create a chart object
  fabric.charts.createChart = function(options, mouse, data, responsive) {
    var chart = { options: options, mouse: mouse, data: data, computed: {} };
    chart.clear = function(){
      var el = document.getElementById(this.options.id);
      var ctx = el.getContext('2d');
      ctx.clearRect(0, 0, this.computed.w, this.computed.h);
      var jq = $('#'+chart.options.id).closest('div');
      $(window).off('resize');
      jq.off('mousedown');
      jq.off('mousemove');
      jq.off('mouseleave');
      $(document).off('mouseup');
      jq.off('blur');
      jq.off('dblclick');
      chart.mouse.unbindSwipe(jq);
    };
    chart.resize = function(zoomin) {
      var old = JSON.parse(JSON.stringify(this.data.overview));
      this.data.overview = [];
      var dx = old[1][0]-old[0][0];
      if (zoomin)
        dx = dx * 0.5;
      else
        dx = dx * 1.5;
      var di = zoomin ? 0.5 : 1.5;
      var change = old[old.length-1][0] - old[0][0];
      change = change / 4;
      var n = old.length / 4;
      var startx = old[0][0];
      var endx = old[old.length-1][0];
      startx = zoomin ? (startx + change) : (startx - change);
      endx = zoomin ? (endx - change) : (endx + change);
      var x = startx, i = zoomin ? n : -n;
      do {
        var y = [];
        y.push(x);
        if (0 <= i && i < old.length) {
          y.push(old[Math.floor(i)][1]);
          y.push(old[Math.floor(i)][2]);
            y.push(old[Math.floor(i)][3]);
        } else {
          y.push(0);
          y.push(0);
            y.push(0);
        }
        this.data.overview.push(y);
        x += dx;
        i += di;
      }
      while (x <= endx);
      this.render();
    };
    chart.render = function(offset){
      var el = document.getElementById(this.options.id);
      var ctx = el.getContext('2d');
      var ts = this.data.overview;
      var jq = $(el).closest('div');
      var h = jq.height();
      var w = jq.width();
      if (!offset) {offset=0;}
      var six = 10;
      var five = 8;
      var ratio = 1;
      if (window.devicePixelRatio) {
        ratio = window.devicePixelRatio;
        w = w * window.devicePixelRatio;
        h = h * window.devicePixelRatio;
        six = six * window.devicePixelRatio;
        five = five * window.devicePixelRatio;
      }

      el.height = h;
      el.width = w;
      this.computed.w = w;
      this.computed.h = h;

      this.computed.x0 = ts[0][0];
      this.computed.dX = ts[1][0]-this.computed.x0;
      this.computed.xN = ts[ts.length-1][0]+this.computed.dX;
      this.computed.dx = (this.computed.dX) / (this.computed.xN-this.computed.x0);

      this.computed.pageX = jq.offset().left;

      offset += w*this.computed.dx*0.5;

      ctx.clearRect(0, 0, w, h);
      var maxy = 1;
      var maxSmokin = 0;
      for (var yi=1;yi<=2;++yi) {
        for (var i=0,l=ts.length;i<l;++i) {
          maxy = maxy > ts[i][yi] ? maxy : ts[i][yi];
        }
      }

      var gridy = [];
      if (this.options.log) {
        var major = [1, 10, 100, 1000, 10000, 100000, 1000000, 10000000];
        var n = 0;
        while (major[n] < maxy && n < major.length) {
          var base = major[n++]
          gridy.push([base*1,1.0,true]);
          gridy.push([base*2.5,0.4,false]);
          gridy.push([base*5,0.8,false]);
          gridy.push([base*7.5,0.4,false]);
        }
      } else {
        var gap = Math.floor(Math.pow(10,Math.ceil(Math.log10(maxy)-1)));
        if (gap <= 0) gap = 1;
        var n = gap;
        while (n < maxy) {
          gridy.push([n,0.5,false]);
          n+=gap;
        }
      }

      for (var i= 0,l=ts.length;i<l;++i) {
          maxSmokin = maxSmokin > ts[i][3] ? maxSmokin : ts[i][3];
      }
      if (this.options.log) { maxy = Math.max(Math.log(maxy + 1),0); }

      ctx.lineWidth = ratio == 1.0 ? 0.2 : 1/ratio;//@TODO take out the harcode value
      for(var z = 0; z < gridy.length; ++z) {
        ctx.strokeStyle = gridy[z][2] ? '#ff9933' : '#32cd32'; //@TODO
        ctx.save();
        ctx.globalAlpha = gridy[z][1];

        // take out the harcode value
        var y = gridy[z][0];
        if (this.options.log) { y = Math.max(Math.log(y + 1),0); }
        y = maxy - y;
        y = (h-six)*(y/maxy);
        y = Math.round(y);

        ctx.beginPath();
        ctx.moveTo(0,y);
        ctx.lineTo(w,y);
        ctx.stroke();
        ctx.closePath();

        ctx.restore();
      }
      ctx.lineWidth = ratio == 1.0 ? 0.2 : 1/ratio;
      ctx.strokeStyle = '#32cd32';
      var xscale = 6*60*60*1000; // 1/4 day
      var lastTick = new Date(1000*ts[0][0]);
      lastTick = new Date(lastTick.getFullYear(), lastTick.getMonth(), lastTick.getDate(), 0, 0, 0);
      while (lastTick.getTime() + xscale < 1000*ts[0][0]) {
        lastTick = new Date(lastTick.getTime() + xscale);
      }
      {
        for (var i=0,l=ts.length;i<l;++i) {
          var tick = new Date(1000*(ts[i][0]));
          if (tick.getTime() - lastTick.getTime() >= xscale) {
            var x = offset + (w * (ts[i][0] - ts[0][0])) / (ts[l-1][0] - ts[0][0]);
            x = Math.floor(x);
            ctx.beginPath();
            ctx.moveTo(x, 0);
            ctx.lineTo(x, h);
            ctx.stroke();
            ctx.closePath();
            lastTick = tick;
          }
        }
      }
      for (var yi=2;yi>=1;--yi) {
        ctx.lineWidth = w*this.computed.dx*0.92;
        if (this.options.colors) {
          ctx.strokeStyle = this.options.colors[yi-1];
        }
        ctx.beginPath();
        for (var i=0,l=ts.length;i<l;++i) {
          var x = offset + (w*(ts[i][0]-ts[0][0]))/(ts[l-1][0]-ts[0][0]);
          var y = ts[i][yi];
          if (this.options.log) { y = Math.max(Math.log(y),0); }
          y = maxy - y;
          y = (h-six)*(y/maxy);
          ctx.moveTo(x,h-six);
          ctx.lineTo(x,y);
        }
        ctx.stroke();
        ctx.closePath();
      }
      if (maxSmokin > 0) {
        maxSmokin = Math.max(Math.log(maxSmokin + 1),0);
        ctx.lineWidth = w*this.computed.dx*0.92;
        for (var i = 0, l = ts.length; i < l; ++i) {
          if (ts[i][3] == 0) continue;
          var x = offset + (w*(ts[i][0]-ts[0][0]))/(ts[l-1][0]-ts[0][0]);
          var percent = Math.max(Math.log(ts[i][3] + 1),0)/maxSmokin;
          if (percent > 0.8) {
            ctx.strokeStyle = '#ff5817';
          } else if (percent > 0.6) {
            ctx.strokeStyle = '#f4ac31';
          } else if (percent > 0.4) {
            ctx.strokeStyle = '#fcd44c';
          } else if (percent > 0.2) {
            ctx.strokeStyle = '#fcdf89';
          } else {
            ctx.strokeStyle = '#f9fae2';
          }
          ctx.beginPath();
          ctx.moveTo(x,h-five);
          ctx.lineTo(x,h);
          ctx.stroke();
          ctx.closePath();
        }
      }
    };
    if (data) {
      chart.render();
      $(window).resize(function() { responsive(); });
      var jq = $('#'+chart.options.id).closest('div');
      jq.mousedown(chart.mouse.down);
      jq.on('touchstart', chart.mouse.touchstart);
      jq.mousemove(chart.mouse.move);
      jq.on('touchmove', chart.mouse.touchmove);
      jq.mouseleave(chart.mouse.up);
      jq.mouseup(chart.mouse.up);
      jq.on('touchend', chart.mouse.touchend);;
      jq.blur(chart.mouse.up);
      jq.dblclick(chart.mouse.doubleclick);
      chart.mouse.bindSwipe(jq, chart.mouse.swipeLeft, chart.mouse.swipeRight);
    }
    return chart;
  }

})(window, document, jQuery);
