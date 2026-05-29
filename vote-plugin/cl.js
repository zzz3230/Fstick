const castVote  = backend.makeCommand('vote.cast');
const resetVote = backend.makeCommand('vote.reset');

const LABELS = {
    yes: '👍 Да!',
    no: '👎 Нет!',
    abstain: '🤷 Воздержался!',
};

const OPTIONS = [
    { key: 'yes', label: LABELS.yes, title: 'Да' },
    { key: 'no', label: LABELS.no, title: 'Нет' },
    { key: 'abstain', label: LABELS.abstain, title: 'Воздержался' },
];

// безопасное обновление счётчика (не ломается на null/undefined)
const inc = (ref) => {
    const v = Number(ref.value) || 0;
    ref.set(v + 1);
};

// кнопка голосования
function VoteBtn({ label, choice, countRef }) {
    return Button(label, async () => {
        await castVote({ choice }).before(() => {
            inc(countRef);
            state.user_choices.choice.set(choice);
        });
    }, { weight: 1 });
}

// блок счётчика
function CountBlock({ title, ref }) {
    return Column({ gap: 4 }, [
        Text(title, { content_align: 'center' }),
        Text(ref, {
            content_align: 'center',
            font_size: 'title',
        }),
    ]);
}

DSL_CONTEXT.exports.__app = Column({ gap: 16 }, [

    Text('📊 Голосование', {
        content_align: 'center',
        font_size: 'title',
    }),

    IfBlock(() => state.user_choices.choice.value === "yes", [
        Text('Yes!')
    ]),
    IfBlock(() => state.user_choices.choice.value === "no", [
        Text('No!')
    ]),
    IfBlock(() => state.user_choices.choice.value === "abstain", [
        Text('Abstain!')
    ]),

    OnUpdate(state.user_choices.choice, [
            Text("Rnd " + Math.random())
    ]),

    Row({ gap: 8 }, OPTIONS.map(opt =>
        VoteBtn({
            label: opt.label,
            choice: opt.key,
            countRef: state.counts[opt.key],
        })
    )),

    Row({ gap: 24, self_align: 'center' }, OPTIONS.map(opt =>
        CountBlock({
            title: opt.title,
            ref: state.counts[opt.key],
        })
    )),

    Text(state.user_choices.choice, {
        content_align: 'center',
        font_size: 'small',
    }),

    Button('🔄 Сбросить', async () => {
        await resetVote({}).before(() => {
            state.counts.yes.set(0);
            state.counts.no.set(0);
            state.counts.abstain.set(0);
        });
    }),

]);

/*
у меня есть система плагинов с кодом фронта и бэка, идея ее в том что состояние автоматически синхронизируется с клиентом + формируется индивидуально. Изучи систему и напиши нормальный плагин, где можно будет создавать 
*/