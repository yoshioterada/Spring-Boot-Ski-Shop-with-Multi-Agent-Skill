import type { Metric } from 'web-vitals';

const vitalsUrl = '/api/vitals';

function sendMetric(metric: Metric) {
  const body = {
    id: metric.id,
    name: metric.name,
    value: metric.value,
    rating: metric.rating,
    delta: metric.delta,
    navigationType: metric.navigationType,
  };

  // Use sendBeacon for reliability on page unload
  if (navigator.sendBeacon) {
    navigator.sendBeacon(vitalsUrl, JSON.stringify(body));
  } else {
    fetch(vitalsUrl, {
      body: JSON.stringify(body),
      method: 'POST',
      keepalive: true,
    });
  }
}

export function reportWebVitals() {
  import('web-vitals').then(({ onCLS, onFCP, onINP, onLCP, onTTFB }) => {
    onCLS(sendMetric);
    onFCP(sendMetric);
    onINP(sendMetric);
    onLCP(sendMetric);
    onTTFB(sendMetric);
  });
}
