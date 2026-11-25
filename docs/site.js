function initTextReveal() {
  if ("IntersectionObserver" in window) {
    const revealObserver = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add("revealed")
          revealObserver.unobserve(entry.target)
        }
      }
    }, { rootMargin: "0px 0px 12px 0px" })

    document.querySelectorAll("[data-reveal]").forEach((element) => revealObserver.observe(element))
  }
}

function initProxyDemo() {
  const demo = document.querySelector("[data-proxy-demo]")
  const demoButton = demo.querySelector(".demo-toggle")
  const demoScreen = demo.querySelector("img")
  const idleScreen = demoScreen.src
  const runningScreen = new Image()
  runningScreen.src = demoScreen.dataset.runningSrc

  demoButton.hidden = false
  demoButton.addEventListener("click", () => {
    const connected = demoButton.getAttribute("aria-pressed") === "false"
    demoButton.setAttribute("aria-pressed", String(connected))
    demo.dataset.connected = String(connected)
    demoScreen.src = connected ? runningScreen.src : idleScreen
  })
}

function initGlassEffects() {
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)")

  for (const card of document.querySelectorAll("[data-spotlight]")) {
    card.addEventListener("pointermove", (event) => {
      if (reducedMotion.matches || event.pointerType === "touch") return

      const bounds = card.getBoundingClientRect()
      card.style.setProperty("--spot-x", `${event.clientX - bounds.left}px`)
      card.style.setProperty("--spot-y", `${event.clientY - bounds.top}px`)
    })
  }

  for (const button of document.querySelectorAll("[data-specular]")) {
    button.addEventListener("pointermove", (event) => {
      if (reducedMotion.matches || event.pointerType === "touch" || button.getAttribute("aria-disabled") === "true") return

      const { left, top, width, height } = button.getBoundingClientRect()
      button.style.setProperty("--specular-x", `${(event.clientX - left) / width * 100}%`)
      button.style.setProperty("--specular-y", `${(event.clientY - top) / height * 100}%`)
    })
    button.addEventListener("pointerleave", () => {
      button.style.removeProperty("--specular-x")
      button.style.removeProperty("--specular-y")
    })
  }
}

function initSectionNavigation() {
  const sectionLinks = Array.from(document.querySelectorAll(".site-nav a"), (link) => ({
    link,
    section: document.getElementById(link.hash.slice(1)),
  })).filter(({ section }) => section)
  let currentSectionLink = null
  let sectionFrame = 0

  function updateSectionLinks() {
    sectionFrame = 0
    const readingLine = Math.min(240, window.innerHeight * .32)
    let nextLink = sectionLinks[0]?.link ?? null
    for (const { link, section } of sectionLinks) {
      if (section.getBoundingClientRect().top <= readingLine) nextLink = link
    }

    if (window.scrollY > 0 && window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 2) {
      nextLink = sectionLinks.at(-1)?.link ?? nextLink
    }
    if (nextLink === currentSectionLink) return

    currentSectionLink?.removeAttribute("aria-current")
    nextLink?.setAttribute("aria-current", "location")
    currentSectionLink = nextLink
  }

  function scheduleSectionUpdate() {
    if (!sectionFrame) sectionFrame = requestAnimationFrame(updateSectionLinks)
  }

  updateSectionLinks()
  window.addEventListener("scroll", scheduleSectionUpdate, { passive: true })
  window.addEventListener("resize", scheduleSectionUpdate)
  window.addEventListener("hashchange", scheduleSectionUpdate)
  window.addEventListener("pageshow", scheduleSectionUpdate)
}

function initRouteDiagram() {
  const routeConsole = document.querySelector(".route-console")
  const routeControls = routeConsole.querySelector(".route-controls")
  const routeHop = routeConsole.querySelector("[data-hop]")
  const routeDescription = routeConsole.querySelector("[data-route-description]")
  const routeButtons = routeControls.querySelectorAll("[data-mode]")
  const routes = {
    direct: {
      label: routeHop.textContent,
      description: routeDescription.textContent,
    },
    cloudflare: {
      label: "WSS через Cloudflare",
      description: "Если прямой WebSocket не срабатывал, движок мог отправить трафик по защищённому WebSocket через Cloudflare. Для этого должен был быть доступен и настроен соответствующий маршрут.",
    },
    tcp: {
      label: "Прямой TCP",
      description: "Когда подходящего маршрута через WebSocket не оставалось, прокси мог открыть обычное TCP-соединение, если был известен адрес нужного дата-центра.",
    },
  }

  for (const button of routeButtons) {
    button.addEventListener("click", () => {
      if (button.getAttribute("aria-pressed") === "true") return

      const mode = button.dataset.mode
      routeConsole.dataset.route = mode
      routeHop.textContent = routes[mode].label
      routeDescription.textContent = routes[mode].description
      for (const control of routeButtons) {
        control.setAttribute("aria-pressed", String(control === button))
      }
    })
  }

  routeControls.hidden = false
}

initTextReveal()
initProxyDemo()
initGlassEffects()
initSectionNavigation()
initRouteDiagram()
