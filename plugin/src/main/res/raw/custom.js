/**
 * FanData 段评脚本 - 注入 WebView 实现段落评论功能
 * 
 * 功能:
 * 1. 在每个段落右侧显示评论标记
 * 2. 点击标记显示评论浮层
 * 3. 支持 SVG 标记和坐标定位
 * 
 * 移植自 Legado 段评功能
 */
(function() {
    'use strict';
    
    const CONFIG = {
        markerWidth: 4,
        markerColor: 'rgba(66, 133, 244, 0.3)',
        markerHoverColor: 'rgba(66, 133, 244, 0.6)',
        panelBg: '#ffffff',
        panelShadow: '0 2px 12px rgba(0,0,0,0.15)',
        panelRadius: 8,
        animationDuration: 200
    };

    // 检查是否已注入
    if (window.__fandata_paragraph_review__) return;
    window.__fandata_paragraph_review__ = true;

    /**
     * 创建 SVG 容器
     */
    function createSvgOverlay() {
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('id', 'fandata-pr-svg');
        svg.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:100%;pointer-events:none;z-index:999;';
        document.body.style.position = 'relative';
        document.body.appendChild(svg);
        return svg;
    }

    /**
     * 创建评论浮层面板
     */
    function createPanel() {
        const panel = document.createElement('div');
        panel.id = 'fandata-pr-panel';
        panel.style.cssText = [
            'display:none',
            'position:fixed',
            'bottom:0',
            'left:0',
            'right:0',
            'max-height:50vh',
            'background:' + CONFIG.panelBg,
            'box-shadow:' + CONFIG.panelShadow,
            'border-radius:' + CONFIG.panelRadius + 'px ' + CONFIG.panelRadius + 'px 0 0',
            'z-index:10000',
            'overflow-y:auto',
            'padding:16px',
            'transition:transform ' + CONFIG.animationDuration + 'ms ease',
            'transform:translateY(100%)'
        ].join(';');
        
        panel.innerHTML = [
            '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">',
            '  <span style="font-size:16px;font-weight:bold;color:#333">段落评论</span>',
            '  <button id="fandata-pr-close" style="border:none;background:none;font-size:20px;cursor:pointer;padding:4px 8px">&times;</button>',
            '</div>',
            '<div id="fandata-pr-content" style="color:#666;font-size:14px;line-height:1.6"></div>'
        ].join('\n');
        
        document.body.appendChild(panel);
        
        // 关闭按钮
        panel.querySelector('#fandata-pr-close').addEventListener('click', function() {
            hidePanel();
        });
        
        return panel;
    }

    var svgOverlay = null;
    var panel = null;
    var markers = [];

    /**
     * 为段落添加标记
     */
    function addMarkers() {
        if (!svgOverlay) svgOverlay = createSvgOverlay();
        if (!panel) panel = createPanel();

        // 清除旧标记
        while (svgOverlay.firstChild) svgOverlay.removeChild(svgOverlay.firstChild);
        markers = [];

        var paragraphs = document.querySelectorAll('p, .content-line, .read-content p, [data-paragraph]');
        if (paragraphs.length === 0) {
            paragraphs = document.querySelectorAll('div > span, div > text');
        }
        
        paragraphs.forEach(function(p, index) {
            var rect = p.getBoundingClientRect();
            if (rect.height < 10) return;
            
            var scrollTop = window.pageYOffset || document.documentElement.scrollTop;
            var scrollLeft = window.pageXOffset || document.documentElement.scrollLeft;
            
            // 创建 SVG 矩形标记
            var marker = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
            marker.setAttribute('x', scrollLeft + rect.right - CONFIG.markerWidth - 2);
            marker.setAttribute('y', scrollTop + rect.top);
            marker.setAttribute('width', CONFIG.markerWidth);
            marker.setAttribute('height', rect.height);
            marker.setAttribute('fill', CONFIG.markerColor);
            marker.setAttribute('rx', '2');
            marker.setAttribute('data-index', index);
            marker.style.cssText = 'pointer-events:all;cursor:pointer;transition:fill 0.15s ease';
            
            marker.addEventListener('mouseenter', function() {
                this.setAttribute('fill', CONFIG.markerHoverColor);
            });
            marker.addEventListener('mouseleave', function() {
                this.setAttribute('fill', CONFIG.markerColor);
            });
            marker.addEventListener('click', function(e) {
                e.stopPropagation();
                showComments(p.textContent.trim(), index, rect);
            });
            
            svgOverlay.appendChild(marker);
            markers.push({ element: p, marker: marker, index: index });
        });
    }

    /**
     * 显示评论面板
     */
    function showComments(paragraphText, index, rect) {
        var content = panel.querySelector('#fandata-pr-content');
        
        // 截取段落前50字作为标题
        var preview = paragraphText.substring(0, 50) + (paragraphText.length > 50 ? '...' : '');
        content.innerHTML = [
            '<div style="padding:12px;background:#f5f5f5;border-radius:6px;margin-bottom:12px;font-size:13px;color:#888">',
            '  <div style="margin-bottom:4px;font-size:12px;color:#aaa">第 ' + (index + 1) + ' 段</div>',
            '  <div>' + escapeHtml(preview) + '</div>',
            '</div>',
            '<div style="text-align:center;color:#999;padding:20px 0">',
            '  <div style="font-size:24px;margin-bottom:8px">💬</div>',
            '  <div>暂无评论</div>',
            '</div>'
        ].join('\n');
        
        panel.style.display = 'block';
        setTimeout(function() {
            panel.style.transform = 'translateY(0)';
        }, 10);
    }

    /**
     * 隐藏评论面板
     */
    function hidePanel() {
        if (!panel) return;
        panel.style.transform = 'translateY(100%)';
        setTimeout(function() {
            panel.style.display = 'none';
        }, CONFIG.animationDuration);
    }

    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // 点击空白处关闭面板
    document.addEventListener('click', function(e) {
        if (panel && !panel.contains(e.target) && !e.target.closest('rect')) {
            hidePanel();
        }
    });

    // 滚动时更新标记位置
    var scrollTimer = null;
    window.addEventListener('scroll', function() {
        if (scrollTimer) clearTimeout(scrollTimer);
        scrollTimer = setTimeout(function() {
            addMarkers();
        }, 150);
    });

    // 页面加载完成后初始化
    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        setTimeout(addMarkers, 500);
    } else {
        document.addEventListener('DOMContentLoaded', function() {
            setTimeout(addMarkers, 500);
        });
    }

    // 暴露全局接口供外部调用
    window.FanDataParagraphReview = {
        refresh: addMarkers,
        hide: hidePanel,
        show: showComments
    };
})();