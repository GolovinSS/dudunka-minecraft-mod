"""Inspect actual baked adult pose geometry/UVs from Gradle verifyModels, not a game screenshot."""
from pathlib import Path
import json,numpy as np
from PIL import Image
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.collections import PolyCollection
root=Path(__file__).resolve().parents[1];models=json.loads((root/'build/character-moments-preview.json').read_text());fig,axes=plt.subplots(3,3,figsize=(12,10));fig.patch.set_facecolor('#f5f0e7')
labels={'dudunka':['Цветок','Маруся','Сюся'],'marusya':['Клубком','Мордочка на лапах','Лапка вперёд'],'syusya':['Спокойные усики','Интерес','Втянутые усики']}
for m in models:
 k=m['kind'];v=m['variant'];row=['dudunka','marusya','syusya'].index(k);ax=axes[row,v];tex=np.asarray(Image.open(root/f'src/main/resources/assets/dudunka/textures/entity/{k}_adult.png').convert('RGB'))/255;faces=[]
 for face in np.asarray(m['vertices']).reshape(-1,4,5):
  xyz=face[:,:3]*16;xyz[:,1]=24-xyz[:,1];uv=face[:,3:].mean(axis=0);color=tex[min(255,int(uv[1]*256)),min(255,int(uv[0]*256))]
  if k=='dudunka':xy=xyz[:,[0,1]];depth=-xyz[:,2].mean()
  else:xy=np.column_stack((xyz[:,0]*.85+xyz[:,2]*.55,xyz[:,1]+xyz[:,0]*.12-xyz[:,2]*.16));depth=(xyz[:,0]-xyz[:,2]+xyz[:,1]*.15).mean()
  faces.append((depth,xy,color))
 faces.sort(key=lambda f:f[0]);ax.add_collection(PolyCollection([f[1] for f in faces],facecolors=[f[2] for f in faces],edgecolors='none'));ax.set(xlim=(-7,7) if k=='dudunka' else (-11,14) if k=='marusya' else (-8,10),ylim=(-1,12) if k=='dudunka' else (-2,14) if k=='marusya' else (-1,7),aspect='equal');ax.axis('off');ax.set_title(labels[k][v],fontsize=12)
fig.suptitle('Больше характера · реальные модели и позы 0.22.0',fontsize=18);fig.tight_layout();out=root/'docs/CHARACTER-MOMENTS-PREVIEW.png';fig.savefig(out,dpi=130,facecolor=fig.get_facecolor());plt.close(fig);print(out)
