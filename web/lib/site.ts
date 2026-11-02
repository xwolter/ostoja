export const site = {
  name: 'Ostoja',
  author: 'xwolter',
  repo: 'https://github.com/xwolter/ostoja',
  versions: { windows: '1.3.0', android: '1.2.1', firefox: '1.1.8' },
};

/** Pliki z najnowszego wydania na GitHubie - nazwy bez numeru wersji, więc linki się nie starzeją. */
export const download = (file: string) => `${site.repo}/releases/latest/download/${file}`;
