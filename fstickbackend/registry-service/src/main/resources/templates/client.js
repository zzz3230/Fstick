const increment = backend.makeCommand('counter.increment');

DSL_CONTEXT.exports.__app = Column({ gap: 8 }, [
    Text(state.counter, { content_align: 'center', font_size: 'title' }),
    Button('+1', async () => {
        await increment({ by: 1 }).before(() => state.counter.set((Number(state.counter.value) || 0) + 1));
    }),
]);
