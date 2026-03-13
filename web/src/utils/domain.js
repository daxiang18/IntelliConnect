import { asyncRoutes } from '@/router/asyncRoutes'

const DOMAIN_ROUTE_ALIASES = {
  '/hub': '/home',
  '/hub/home': '/home',
  '/hub/inbox': '/home',
  '/hub/notes': '/home',
  '/hub/knowledge': '/productKnowledge',
  '/hub/graph': '/knowledgeGraphic',
  '/hub/memory': '/agentLongMemory',
  '/hub/settings': '/setting',
  '/iot': '/product',
  '/iot/product': '/product',
  '/iot/dashboard': '/deviceData',
  '/iot/monitor': '/deviceData',
  '/iot/models': '/productModel',
  '/iot/ota': '/productOta',
}

const ROUTE_DOMAIN_LOOKUP = buildRouteDomainLookup(asyncRoutes)

export function normalizeDomainPath(targetPath) {
  if (!targetPath) return '/home'
  const { pathname, suffix } = splitTargetPath(targetPath)
  return `${DOMAIN_ROUTE_ALIASES[pathname] || pathname}${suffix}`
}

export function resolvePathDomain(targetPath) {
  const normalizedPath = normalizeDomainPath(targetPath)
  return ROUTE_DOMAIN_LOOKUP[splitTargetPath(normalizedPath).pathname] || null
}

export function canAccessPath(domainState, targetPath) {
  const routeDomain = resolvePathDomain(targetPath)

  if (!routeDomain) return false
  if (routeDomain === 'shared') return true
  if (routeDomain === 'hub') return !!domainState?.hub?.enabled
  if (routeDomain === 'iot') return !!domainState?.iot?.enabled

  return true
}

export function resolveDomainEntry(domainState, domainKey) {
  const domainConfig = domainState && domainState[domainKey]
  const defaultEntries = {
    hub: '/home',
    iot: '/product',
  }
  const configuredPath = normalizeDomainPath(domainConfig && domainConfig.defaultEntry)

  if (canAccessPath(domainState, configuredPath)) {
    return configuredPath
  }

  const fallbackPath = defaultEntries[domainKey] || '/home'
  if (canAccessPath(domainState, fallbackPath)) {
    return fallbackPath
  }

  return resolveTargetPath(domainState)
}

export function resolveTargetPath(domainState, requestedPath) {
  const normalizedPath = requestedPath && requestedPath !== '/' ? normalizeDomainPath(requestedPath) : ''

  if (normalizedPath && canAccessPath(domainState, normalizedPath)) {
    return normalizedPath
  }

  if (domainState && domainState.hub && domainState.hub.enabled) {
    return '/home'
  }

  if (domainState && domainState.iot && domainState.iot.enabled) {
    return resolveDomainEntry(domainState, 'iot')
  }

  return '/home'
}

function buildRouteDomainLookup(routes, lookup = {}) {
  routes.forEach((route) => {
    if (route.path) {
      lookup[route.path] = route.meta?.domain || 'shared'
    }

    if (route.children?.length) {
      buildRouteDomainLookup(route.children, lookup)
    }
  })

  return lookup
}

function splitTargetPath(targetPath) {
  const separatorIndex = targetPath.search(/[?#]/)

  if (separatorIndex === -1) {
    return {
      pathname: targetPath,
      suffix: '',
    }
  }

  return {
    pathname: targetPath.slice(0, separatorIndex),
    suffix: targetPath.slice(separatorIndex),
  }
}
