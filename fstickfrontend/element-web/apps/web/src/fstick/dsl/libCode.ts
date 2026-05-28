/*
Copyright 2025 New Vector Ltd.

SPDX-License-Identifier: AGPL-3.0-only OR GPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE files in the repository root for full details.
*/

export default `const V='1';function m(t,p,c,x){return{__dsl_node:true,version:V,type:t,props:p??{},children:c??[],...x};}function Column(p,c){return m('Column',p,c);}function Row(p,c){return m('Row',p,c);}function Text(ct,p){return m('Text',p,[],{content:ct});}function Button(l,fn,p){return m('Button',p,[],{label:l,handler:fn??null});}function Input(p){return m('Input',p,[]);}DSL_CONTEXT.exports.__lib__={Column,Row,Text,Button,Input};`;
