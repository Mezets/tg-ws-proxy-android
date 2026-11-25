const links = {
  download: "&сслыка&",
  repository: "https://github.com/Mezets/tg-ws-proxy-android",
  releases: "https://github.com/Mezets/tg-ws-proxy-android/releases",
  donation: "",
}

for (const link of document.querySelectorAll("[data-link]")) {
  const destination = links[link.dataset.link]
  if (!destination?.startsWith("https://")) continue

  link.href = destination
  link.target = "_blank"
  link.rel = "noopener noreferrer"
  link.hidden = false

  if (link.hasAttribute("aria-disabled")) {
    link.removeAttribute("aria-disabled")
    link.removeAttribute("role")
  }
}
