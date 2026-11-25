function startDither(canvas) {
  const context = canvas.getContext("2d", { alpha: false })
  if (!context) return

  const motionPreference = window.matchMedia("(prefers-reduced-motion: reduce)")
  const trailLifetime = Number(canvas.dataset.trail) * 1000
  const trail = []
  const threshold = [
    0, 8, 2, 10,
    12, 4, 14, 6,
    3, 11, 1, 9,
    15, 7, 13, 5,
  ]
  const blue = [[7, 12, 24], [10, 17, 34], [14, 23, 46], [20, 34, 65], [28, 45, 85], [36, 55, 104]]
  const violet = [[10, 11, 24], [15, 16, 34], [23, 22, 45], [32, 28, 59], [43, 36, 75], [54, 45, 94]]
  let rows
  let frameId = 0
  let lastFrame = 0
  let lastTrailPoint = -Infinity

  function paint(now) {
    const { width, height } = canvas
    const pixels = rows.data
    const time = now * .00045
    for (let y = 0; y < height; y++) {
      const vertical = y / height
      const bend = Math.sin(vertical * 5.4 + time) * 2.5
      for (let x = 0; x < width; x++) {
        const horizontal = x / width
        const wave = Math.sin(horizontal * 15 + vertical * 9 + bend - time)
        const cross = Math.sin(horizontal * 12 - vertical * 7 + time * .58)
        const light = 2.18 + wave * 1.45 + cross * .78 + Math.abs(horizontal - .5) * 2.05
        const step = threshold[(y % 4) * 4 + x % 4] / 16
        const level = Math.min(blue.length - 1, Math.max(0, Math.floor(light + step)))
        const tint = horizontal * horizontal
        const index = (y * width + x) * 4
        pixels[index] = blue[level][0] + (violet[level][0] - blue[level][0]) * tint
        pixels[index + 1] = blue[level][1] + (violet[level][1] - blue[level][1]) * tint
        pixels[index + 2] = blue[level][2] + (violet[level][2] - blue[level][2]) * tint
        pixels[index + 3] = 255
      }
    }
    context.putImageData(rows, 0, 0)

    while (trail.length && now - trail[0].time >= trailLifetime) trail.shift()
    for (const point of trail) {
      const remaining = Math.max(0, 1 - (now - point.time) / trailLifetime)
      const x = point.x * width
      const y = point.y * height
      const radius = 9 + (1 - remaining) * 15
      const light = context.createRadialGradient(x, y, 0, x, y, radius)
      light.addColorStop(0, `rgba(139, 167, 255, ${remaining * .18})`)
      light.addColorStop(1, "rgba(139, 167, 255, 0)")
      context.fillStyle = light
      context.fillRect(x - radius, y - radius, radius * 2, radius * 2)
    }
  }

  function resize() {
    const width = canvas.clientWidth
    const height = canvas.clientHeight
    const cell = Math.max(3.6, width / 520, height / 360)
    canvas.width = Math.max(1, Math.ceil(width / cell))
    canvas.height = Math.max(1, Math.ceil(height / cell))
    rows = context.createImageData(canvas.width, canvas.height)
    paint(motionPreference.matches ? 0 : performance.now())
  }

  function frame(now) {
    if (now - lastFrame >= 40) {
      paint(now)
      lastFrame = now
    }
    frameId = requestAnimationFrame(frame)
  }

  function syncAnimation() {
    cancelAnimationFrame(frameId)
    if (document.hidden) return
    if (motionPreference.matches) {
      trail.length = 0
      paint(0)
      return
    }
    frameId = requestAnimationFrame(frame)
  }

  window.addEventListener("pointermove", (event) => {
    if (motionPreference.matches || event.pointerType === "touch") return
    const now = performance.now()
    if (now - lastTrailPoint < 40) return
    lastTrailPoint = now
    trail.push({
      x: event.clientX / canvas.clientWidth,
      y: event.clientY / canvas.clientHeight,
      time: now,
    })
  }, { passive: true })

  canvas.hidden = false
  resize()
  syncAnimation()
  window.addEventListener("resize", resize)
  motionPreference.addEventListener("change", syncAnimation)
  document.addEventListener("visibilitychange", syncAnimation)
}

startDither(document.querySelector(".dither-background"))
