log.info "output; foo=${memory.foo}; bar=${memory.bar}; delete=${memory.delete}; init=${memory.init}; global=${memory.global}"

// Global property that name collides with Function argument(s) should remain
// in tact.
assert memory.init == 'original'

// Global property that does not name collide with Function argument(s) should
// remain in tact
assert memory.global == 'global'

// Required Function argument that does not exist outside the Pickle scope
assert memory.isNothing('foo')

// Optional Function argument that does not exist outside the Pickle scope
assert memory.isNothing('bar')

// Arbitrary property set within a library task that should only live within
// the Pickle scope
assert memory.isNothing('delete')

// Complex property set within a library task that should only live within
// the Pickle scope
assert memory.isNothing('closure')