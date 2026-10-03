"""Orthographic geometry preview from verifyModels' actual baked vertex export.
Run Gradle verifyModels first, then this script (Pillow, numpy, matplotlib).
Preview samples each face's UV center; it is not an in-game screenshot.
"""
from pathlib import Path
import json, sys, numpy as np
from PIL import Image
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.collections import PolyCollection
r=Path(__file__).resolve().parents[1]
kind=sys.argv[1] if len(sys.argv)>1 else 'dudunka'
if kind not in ('dudunka','syusya','marusya'):raise ValueError('Expected dudunka, syusya or marusya')
data=[m for m in json.loads((r/'build/model-preview.json').read_text()) if m.get('kind','dudunka')==kind]
if kind in ('syusya','marusya'):
    fig,axes=plt.subplots(3,3,figsize=(12,7),gridspec_kw={'width_ratios':[1,2.45,1]});axes=axes.flatten()
else:fig,axes=plt.subplots(1,9,figsize=(18,5))
fig.patch.set_facecolor('#f5f1e9')
for model in data:
    stage=model['stage'];scale=[.55,.715,.88][stage] if kind=='dudunka' else .55
    texture=np.asarray(Image.open(r/'src/main/resources/assets/dudunka/textures/entity'/['palette.png',kind+'_teen.png',kind+'_adult.png'][stage]).convert('RGB'))/255
    vertices=np.asarray(model['vertices']).reshape(-1,4,5)
    for view in range(3):
        ax=axes[stage*3+view];faces=[]
        for face in vertices:
            xyz=face[:,:3]*16;xyz[:,1]=24-xyz[:,1];xyz*=scale
            uv=np.mean(face[:,3:],axis=0);color=texture[min(255,int(uv[1]*256)),min(255,int(uv[0]*256))]
            if view==0:xy=xyz[:,[0,1]];depth=-xyz[:,2].mean()
            elif view==1:xy=xyz[:,[2,1]];depth=xyz[:,0].mean()
            else:xy=xyz[:,[0,1]]*np.array([-1,1]);depth=xyz[:,2].mean()
            faces.append((depth,xy,color))
        faces.sort(key=lambda item:item[0]);ax.add_collection(PolyCollection([x[1] for x in faces],facecolors=[x[2] for x in faces],edgecolors='none'))
        ax.set(xlim=(-3.3,3.3) if kind=='dudunka' else (((-6,8) if view==1 else (-3,3)) if kind=='marusya' else ((-4.9,4.9) if view==1 else (-2,2))),ylim=(-.2,10.2) if kind=='dudunka' else ((-.2,10) if kind=='marusya' else (-.2,5)),aspect='equal');ax.axis('off')
        ax.set_title(['Малыш','Подросток','Взрослая'][stage]+'\n'+['Спереди','Сбоку','Сзади'][view],fontsize=10)
fig.suptitle(('Дюдюнька: геометрия моделей 0.8.0-alpha' if kind=='dudunka' else ('Сюся: геометрия моделей 0.9.0-alpha' if kind=='syusya' else 'Маруся: геометрия моделей 0.10.0-alpha'))+' • общий масштаб',fontsize=15)
fig.tight_layout();fig.savefig(r/f'docs/{kind.upper()}-AGES-PREVIEW.png',dpi=120,facecolor=fig.get_facecolor());plt.close(fig)
