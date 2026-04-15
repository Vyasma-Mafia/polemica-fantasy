/** For older WebKit (e.g. Telegram on iOS): @telegram-apps/sdk may rely on Object.hasOwn. */
if (!Object.hasOwn) {
  Object.defineProperty(Object, 'hasOwn', {
    value(obj: object, prop: PropertyKey): boolean {
      return Object.prototype.hasOwnProperty.call(obj, prop)
    },
    configurable: true,
    writable: true,
    enumerable: false,
  })
}
