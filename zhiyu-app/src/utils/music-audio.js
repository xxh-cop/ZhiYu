// Native audio on each platform. H5 catches play cancellation instead of leaking an unhandled promise.
export function createMusicAudio() {
  // #ifdef H5
  const element = new Audio()
  element.preload = 'metadata'
  const listeners = []
  let errorHandler = () => {}, disposed = false
  const on = (event, fn) => { element.addEventListener(event, fn); listeners.push([event, fn]) }
  return {
    get src() { return element.src }, set src(value) { element.src = value },
    get volume() { return element.volume }, set volume(value) { element.volume = value },
    get autoplay() { return false }, set autoplay(value) { element.autoplay = value },
    get currentTime() { return element.currentTime },
    get duration() { return Number.isFinite(element.duration) ? element.duration : 0 },
    play() { element.play().catch(error => { if (!disposed && error.name !== 'AbortError') errorHandler(error) }) },
    pause() { element.pause() },
    stop() { element.pause(); if (element.readyState) element.currentTime = 0 },
    seek(value) { if (element.readyState) element.currentTime = value },
    onPlay(fn) { on('playing', fn) }, onPause(fn) { on('pause', fn) }, onEnded(fn) { on('ended', fn) },
    onTimeUpdate(fn) { on('timeupdate', fn); on('loadedmetadata', fn) },
    onError(fn) { errorHandler = fn; on('error', fn) },
    destroy() { disposed = true; listeners.forEach(([event, fn]) => element.removeEventListener(event, fn)); element.pause(); element.removeAttribute('src'); element.load() },
  }
  // #endif
  // #ifndef H5
  return uni.createInnerAudioContext()
  // #endif
}
