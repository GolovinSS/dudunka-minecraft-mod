"""Read-only saved-region verification of naturally generated Stage 3.2d cottages.
Reads existing Anvil/NBT data with Python's standard library; never starts worldgen.
Usage: python3 tools/inspect_vale_stage32d_world.py build/reports/mushroom-vale-stage32d/survey-final-...
"""
from pathlib import Path
from functools import lru_cache
from collections import Counter, defaultdict
import argparse, gzip, hashlib, io, json, re, struct, zlib
ROOT=Path(__file__).resolve().parents[1]
class NBT:
    def __init__(self,data): self.f=io.BytesIO(data)
    def num(self,fmt): return struct.unpack('>'+fmt,self.f.read(struct.calcsize('>'+fmt)))[0]
    def string(self): return self.f.read(self.num('H')).decode('utf-8')
    def value(self,t):
        if t in range(1,7): return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
        if t==7: return self.f.read(self.num('i'))
        if t==8: return self.string()
        if t==9:
            subtype=self.num('B'); return [self.value(subtype) for _ in range(self.num('i'))]
        if t==10:
            result={}
            while (subtype:=self.num('B'))!=0:
                key=self.string();result[key]=self.value(subtype)
            return result
        if t in (11,12): return [self.num('i' if t==11 else 'q') for _ in range(self.num('i'))]
        raise ValueError(t)
    def root(self):
        assert self.num('B')==10;self.string();return self.value(10)

def palette_value(container,index,minimum):
    palette=container['palette']
    if len(palette)==1:return palette[0]
    bits=max(minimum,(len(palette)-1).bit_length()); per=64//bits
    return palette[(container['data'][index//per]>>((index%per)*bits))&((1<<bits)-1)]

def inspect(directory):
    log=(directory/'gametest.log').read_text()
    assert 'BUILD SUCCESSFUL' in log and 'All 1 required tests passed' in log,'Requires completed, successful survey'
    region=ROOT/'run'/('vale-stage32d-'+directory.name)/'world/dimensions/dudunka/mushroom_vale/region'
    statuses={}
    @lru_cache(maxsize=256)
    def chunk(cx,cz):
        path=region/f'r.{cx//32}.{cz//32}.mca'
        with path.open('rb') as f:
            f.seek(4*((cx%32)+(cz%32)*32)); entry=int.from_bytes(f.read(4),'big');assert entry>>8,'Saved chunk missing'
            f.seek((entry>>8)*4096);length=int.from_bytes(f.read(4),'big');compression=f.read(1)[0];data=f.read(length-1)
        assert compression in (1,2,3),'External or unsupported Anvil storage'
        data=gzip.decompress(data) if compression==1 else zlib.decompress(data) if compression==2 else data
        value=NBT(data).root();statuses[(cx,cz)]=value['Status']
        return {s['Y']:s for s in value['sections']}
    def block(x,y,z):
        section=chunk(x//16,z//16).get(y//16)
        if not section or 'block_states' not in section:return {'Name':'minecraft:air'}
        return palette_value(section['block_states'],((y%16)*16+(z%16))*16+(x%16),4)
    seed=int(re.search(r'VALE32D_PATCH seed=(-?\d+)',log)[1])
    zoom_seed=int.from_bytes(hashlib.sha256(struct.pack('<q',seed)).digest()[:8],'little')
    def lcg(value,salt):return (value*(value*6364136223846793005+1442695040888963407)+salt)&((1<<64)-1)
    @lru_cache(maxsize=4096)
    def fiddle(qx,qy,qz):
        value=zoom_seed
        for salt in (qx,qy,qz,qx,qy,qz):value=lcg(value,salt)
        result=[]
        for _ in range(3):
            result.append((((value>>24)%1024)/1024-.5)*.9);value=lcg(value,zoom_seed)
        return result
    def biome_at(x,y,z):
        # Exact local Minecraft 1.20.1 BiomeManager fuzzy zoom, rather than
        # attributing boundary blocks to their unzoomed stored quart biome.
        coords=[x-2,y-2,z-2];quarts=[v>>2 for v in coords];fractions=[(v&3)/4 for v in coords]
        best=None;distance=float('inf')
        for corner in range(8):
            offsets=[(corner>>2)&1,(corner>>1)&1,corner&1]
            candidate=[q+o for q,o in zip(quarts,offsets)]
            values=[f-o+j for f,o,j in zip(fractions,offsets,fiddle(*candidate))]
            d=values[2]**2+values[1]**2+values[0]**2
            if d<distance:distance=d;best=candidate
        qx,qy,qz=best
        section=chunk(qx//4,qz//4)[qy//4]
        return palette_value(section['biomes'],((qy%4)*4+qz%4)*4+qx%4,1)
    results=[]
    for name,x,y,z in re.findall(r'VALE32D_COTTAGE seed=\S+ feature=mushroom_vale/(\w+) pos=BlockPos\{x=(-?\d+), y=(-?\d+), z=(-?\d+)\}',log):
        x,y,z=map(int,(x,y,z));config=json.loads((ROOT/f'src/main/resources/data/dudunka/worldgen/configured_feature/mushroom_vale/{name}.json').read_text())['config']
        scores=[]
        for rotation in range(4):
            matched=0
            for piece in config['blocks']:
                dx,dy,dz=piece['offset']
                for _ in range(rotation):dx,dz=-dz,dx
                actual=block(x+dx,y+dy,z+dz);expected=piece['state']
                if actual['Name']!=expected['Name']:continue
                properties=dict(expected.get('Properties',{}))
                if 'facing' in properties:
                    directions=['north','east','south','west'];properties['facing']=directions[(directions.index(properties['facing'])+rotation)%4]
                if rotation%2 and properties.get('axis') in ('x','z'):properties['axis']='z' if properties['axis']=='x' else 'x'
                if all(actual.get('Properties',{}).get(k)==v for k,v in properties.items() if k not in ('north','east','south','west','up','down')):matched+=1
            scores.append(matched)
        assert max(scores)==len(config['blocks']),(name,(x,y,z),scores,len(config['blocks']))
        results.append({'feature':name,'pos':[x,y,z],'rotation':scores.index(max(scores))*90,'preservedBlocks':max(scores)})
    area=set()
    for x,z in re.findall(r'VALE32D_PATCH seed=\S+ biome=\S+ center=BlockPos\{x=(-?\d+), y=-?\d+, z=(-?\d+)\} chunks=576',log):
        cx,cz=int(x)//16,int(z)//16
        area.update((cx+dx,cz+dz) for dx in range(-12,12) for dz in range(-12,12))
    assert len(area)==1728, len(area)
    totals=defaultdict(Counter)
    wanted={'dudunka:vale_bench','dudunka:vale_lantern'}
    for cx,cz in sorted(area):
        sections=chunk(cx,cz);assert statuses[(cx,cz)]=='minecraft:full', (cx,cz,statuses[(cx,cz)])
        for section in sections.values():
            blocks=section.get('block_states')
            if not blocks or not any(state['Name'] in wanted for state in blocks['palette']):continue
            for i in range(4096):
                state=palette_value(blocks,i,4)
                if state['Name'] not in wanted:continue
                x=i%16;z=(i//16)%16;y=i//256
                biome=biome_at(cx*16+x,section['Y']*16+y,cz*16+z)
                totals[biome][state['Name']]+=1
    return {'survey':directory.name,'savedCottages':results,'verifiedCottages':len(results),
            'fullChunksRead':len(area),'savedDecorBlocksByBiome':{k:dict(v) for k,v in sorted(totals.items())}}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('reports',type=Path,nargs='+');p.add_argument('--output',type=Path);a=p.parse_args()
    result=[inspect(path) for path in a.reports]
    output=json.dumps(result,indent=2)+'\n'
    if a.output:a.output.write_text(output)
    print(output)
