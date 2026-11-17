import { describe, expect, it } from 'vitest';
import { parseAmazonProductUrl } from './product-url';
describe('Amazon India product URL parser', () => {
  it.each([
    ['https://amazon.in/dp/B0D2MW6L54', 'B0D2MW6L54'],
    ['https://www.amazon.in/Samsung-Galaxy-Smartphone-Storage/dp/B0FDKZMMYC', 'B0FDKZMMYC'],
    ['https://amazon.in/dp/B0D2MW6L54?th=1#details', 'B0D2MW6L54'],
    ['https://www.amazon.in/WZATCO-Official-Netflix-Google-Projector/dp/B0H42KDFN8/ref=pd_sbs_d_sccl_1_7/258-9384714-2049055', 'B0H42KDFN8'],
    ['https://www.amazon.in/gp/product/B0D2MW6L54/ref=ewc_pr_img_1?smid=AUCQGA5A6IVIW&psc=1', 'B0D2MW6L54'],
    ['https://www.amazon.in/HAWK-PROAUDIO-Gold-Male-Female/dp/B07D4JBCSY', 'B07D4JBCSY'],
    ['https://www.amazon.in/Spigen-Transparent-Protection-Thermoplastic-Polyurethane/dp/B0GKQFH9VG', 'B0GKQFH9VG'],
    ['https://amazon.in/gp/product/B0D7SJXCPP', 'B0D7SJXCPP'],
    ['https://www.amazon.in/gp/aw/d/B0D2MW6L54?th=1#details', 'B0D2MW6L54'],
    ['https://www.amazon.in/gP/PrOdUcT/b07d4jbcsy/ref=product_page?smid=seller', 'B07D4JBCSY']
  ])('accepts %s', (value, expectedAsin) => { const result=parseAmazonProductUrl(value); expect(result.supported).toBe(true); if(result.supported){expect(result.asin).toBe(expectedAsin);expect(result.canonicalUrl).toBe(`https://www.amazon.in/dp/${expectedAsin}`);} });
  it.each(['https://amazon.com/dp/B0D2MW6L54','https://amazon.in.attacker.com/dp/B0D2MW6L54','http://amazon.in/dp/B0D2MW6L54','https://amazon.in/s?k=x','https://amazon.in/dp/short','https://amazon.in/dp/B0D2MW6L5!','https://amazon.in/name/dp/B0D2MW6L54/extra','https://amazon.in/name/dp/B0D2MW6L54/other','not url'])('rejects %s', value => expect(parseAmazonProductUrl(value).supported).toBe(false));
  it('rejects missing URL',()=>expect(parseAmazonProductUrl()).toEqual({supported:false,reason:'missing_url'}));
});
