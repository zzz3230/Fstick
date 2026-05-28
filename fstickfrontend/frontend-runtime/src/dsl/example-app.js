/**
 * Пример пользовательского DSL-скрипта.
 * В реальности загружается из сети.
 *
 * Доступные глобалы (инжектируются рантаймом):
 *   Column, Row, Text, Button, Input  — UI-компоненты
 *   state    — реактивные ячейки серверного состояния (сгенерированы по схеме)
 *   backend  — клиент для вызова серверных команд
 */

const test_command = backend.makeCommand('test_command');

DSL_CONTEXT.exports.__app = Column({}, [
    Text('TEST', {content_align: 'center', font_size: 'title'}),
    Column({gap: 10}, [
        Text('Enter text and press button:', {content_align: 'left'}),
        Row({self_align: 'center', gap: 8}, [
            Input({placeholder: 'write here', id: 'myInput', weight: 2}),
            Button('Show', async () => {

                const res = await test_command(
                    {key1: 'arg1', key2: 2, key3: 'arg3'}
                ).before(() => {
                    state.statusText.set('loading...');
                });
            }, {weight: 1}),
        ]),
    ]),
    Text(state.statusText, {content_align: 'right', font_size: 'small'}),
    Text(state.field1),
    Row({}, [
        Text("Executed total: ", {content_align: 'left'}),
        Text(state.stats.commands_executed, {content_align: 'right'})
    ]),
    Text(state.txt2),
    Text(state.now, { content_align: 'center', font_size: 'small' }),
]);
