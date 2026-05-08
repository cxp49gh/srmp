export function createLatestRequestGuard() {
  let currentToken = 0

  return {
    next() {
      currentToken += 1
      return currentToken
    },
    invalidate() {
      currentToken += 1
    },
    isCurrent(token: number) {
      return token === currentToken
    }
  }
}
